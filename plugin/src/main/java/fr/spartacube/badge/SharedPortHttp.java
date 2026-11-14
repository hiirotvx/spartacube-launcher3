package fr.spartacube.badge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Sert l'API des actualités sur le port du serveur Minecraft (aucun port à ouvrir).
 * Chaque nouvelle connexion est examinée : si elle commence par « GET  » ou « POST », c'est le
 * launcher qui parle HTTP ; sinon c'est un joueur et le gestionnaire se retire aussitôt.
 * Un paquet Minecraft ne peut jamais commencer par ces octets (le paquet de poignée de main a l'id 0).
 */
public final class SharedPortHttp {
    private static final String KEY_NS = "spartacube";
    private static final String KEY_VALUE = "news_http";

    private final NewsApi api;
    private final Logger log;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SpartaCube-NewsHttp");
        t.setDaemon(true);
        return t;
    });

    public SharedPortHttp(NewsApi api, Logger log) {
        this.api = api;
        this.log = log;
    }

    /** Enregistre l'écouteur via l'API interne de Paper (réflexion : absente sur Spigot). */
    public boolean register() {
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listenerType = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            Class<?> keyType = Class.forName("net.kyori.adventure.key.Key");
            Object key = keyType.getMethod("key", String.class, String.class).invoke(null, KEY_NS, KEY_VALUE);
            Object listener = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{listenerType}, (proxy, method, args) -> {
                if ("afterInitChannel".equals(method.getName()) && args != null && args.length == 1) {
                    install((Channel) args[0]);
                    return null;
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "SpartaCubeNewsListener";
                    default -> null;
                };
            });
            Method add = holder.getMethod("addListener", keyType, listenerType);
            add.invoke(null, key, listener);
            return true;
        } catch (Throwable t) {
            log.warning("API des actualités sur le port Minecraft indisponible (serveur Paper requis) : " + t);
            return false;
        }
    }

    public void unregister() {
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> keyType = Class.forName("net.kyori.adventure.key.Key");
            Object key = keyType.getMethod("key", String.class, String.class).invoke(null, KEY_NS, KEY_VALUE);
            holder.getMethod("removeListener", keyType).invoke(null, key);
        } catch (Throwable ignored) {
            // rien à retirer
        }
        workers.shutdownNow();
    }

    private void install(Channel ch) {
        try {
            ch.pipeline().addFirst("spartacube_news_http", new Sniffer());
        } catch (Throwable t) {
            log.fine("Impossible d'examiner une connexion : " + t);
        }
    }

    private final class Sniffer extends ChannelInboundHandlerAdapter {
        private ByteBuf buffer;
        private boolean http;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf in)) {
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(msg);
                return;
            }
            if (!http) {
                if (in.readableBytes() < 4) {
                    // Trop court pour décider : on laisse passer (un client HTTP envoie la ligne entière d'un coup)
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(msg);
                    return;
                }
                String head = in.toString(in.readerIndex(), 4, StandardCharsets.US_ASCII);
                if (!"GET ".equals(head) && !"POST".equals(head)) {
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(msg);
                    return;
                }
                http = true;
                buffer = ctx.alloc().buffer();
            }
            buffer.writeBytes(in);
            in.release();
            if (buffer.readableBytes() > NewsApi.MAX_REQUEST) {
                respond(ctx, new NewsApi.Resp(413, "{\"error\":\"Requête trop volumineuse.\"}"));
                return;
            }
            tryProcess(ctx);
        }

        private void tryProcess(ChannelHandlerContext ctx) {
            String all = buffer.toString(StandardCharsets.ISO_8859_1);
            int end = all.indexOf("\r\n\r\n");
            if (end < 0) return; // en-têtes incomplets
            String[] lines = all.substring(0, end).split("\r\n");
            String[] first = lines[0].split(" ");
            if (first.length < 2) { respond(ctx, new NewsApi.Resp(400, "{\"error\":\"Requête invalide\"}")); return; }
            int length = 0;
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0 && lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT).equals("content-length")) {
                    try { length = Integer.parseInt(lines[i].substring(c + 1).trim()); } catch (NumberFormatException e) { length = -1; }
                }
            }
            if (length < 0 || length > NewsApi.MAX_REQUEST) { respond(ctx, new NewsApi.Resp(413, "{\"error\":\"Requête trop volumineuse.\"}")); return; }
            int bodyStart = end + 4;
            if (buffer.readableBytes() < bodyStart + length) return; // corps incomplet
            byte[] body = new byte[length];
            buffer.getBytes(buffer.readerIndex() + bodyStart, body);
            String method = first[0];
            String path = first[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            String ip = ctx.channel().remoteAddress() instanceof InetSocketAddress a ? a.getAddress().getHostAddress() : "?";
            final String p = path;
            ctx.channel().config().setAutoRead(false);
            workers.execute(() -> {
                NewsApi.Resp r = api.handle(method, p, ip, body);
                ctx.channel().eventLoop().execute(() -> respond(ctx, r));
            });
        }

        private void respond(ChannelHandlerContext ctx, NewsApi.Resp r) {
            byte[] json = r.json().getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + r.code() + " " + reason(r.code()) + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Content-Length: " + json.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            ByteBuf out = Unpooled.wrappedBuffer(head.getBytes(StandardCharsets.US_ASCII), json);
            ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (buffer != null) { buffer.release(); buffer = null; }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (buffer != null) { buffer.release(); buffer = null; }
            super.channelInactive(ctx);
        }

        private String reason(int code) {
            return switch (code) {
                case 200 -> "OK";
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 413 -> "Payload Too Large";
                case 429 -> "Too Many Requests";
                default -> "Error";
            };
        }
    }
}
