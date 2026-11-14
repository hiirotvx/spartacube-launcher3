package t;
import fr.spartacube.badge.*;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
public class T {
  public static void main(String[] a) throws Exception {
    Path dir = Files.createTempDirectory("n");
    Files.writeString(dir.resolve("news.json"), "[{\"title\":\"Salut\",\"text\":\"x\",\"image\":\"http://bad\"}]");
    NewsApi api = new NewsApi(Logger.getLogger("t"), dir, Set.of());
    api.init();
    SharedPortHttp sp = new SharedPortHttp(api, Logger.getLogger("t"));
    var m = SharedPortHttp.class.getDeclaredMethod("install", Channel.class); m.setAccessible(true);
    List<Object> passed = new ArrayList<>();
    // HTTP
    EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter(){ public void channelRead(ChannelHandlerContext c, Object o){ passed.add(o);} });
    m.invoke(sp, ch);
    ch.writeInbound(Unpooled.copiedBuffer("GET /news HTTP/1.1\r\nHost: x\r\n", StandardCharsets.US_ASCII));
    ch.writeInbound(Unpooled.copiedBuffer("\r\n", StandardCharsets.US_ASCII));
    for (int i=0;i<50 && ch.outboundMessages().isEmpty();i++){ Thread.sleep(50); ch.runPendingTasks(); }
    ByteBuf out = ch.readOutbound();
    System.out.println("HTTP -> " + (out==null?"null":out.toString(StandardCharsets.UTF_8).replace("\r\n"," | ")));
    System.out.println("open after=" + ch.isOpen() + " passed=" + passed.size());
    // challenge
    EmbeddedChannel c2 = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    m.invoke(sp, c2);
    c2.writeInbound(Unpooled.copiedBuffer("GET /news/challenge HTTP/1.1\r\n\r\n", StandardCharsets.US_ASCII));
    for (int i=0;i<50 && c2.outboundMessages().isEmpty();i++){ Thread.sleep(50); c2.runPendingTasks(); }
    ByteBuf o2 = c2.readOutbound(); System.out.println("CHALLENGE -> " + o2.toString(StandardCharsets.UTF_8).replace("\r\n"," | "));
    // POST bad nonce
    EmbeddedChannel c3 = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    m.invoke(sp, c3);
    String body = "{\"username\":\"Hiirowh\",\"nonce\":\"00\",\"payload\":\"[]\"}";
    c3.writeInbound(Unpooled.copiedBuffer("POST /news/publish HTTP/1.1\r\nContent-Length: "+body.length()+"\r\n\r\n"+body.substring(0,10), StandardCharsets.US_ASCII));
    c3.writeInbound(Unpooled.copiedBuffer(body.substring(10), StandardCharsets.US_ASCII));
    for (int i=0;i<50 && c3.outboundMessages().isEmpty();i++){ Thread.sleep(50); c3.runPendingTasks(); }
    ByteBuf o3 = c3.readOutbound(); System.out.println("POST -> " + o3.toString(StandardCharsets.UTF_8).replace("\r\n"," | "));
    // Minecraft handshake
    List<Object> p2 = new ArrayList<>();
    EmbeddedChannel mc = new EmbeddedChannel(new ChannelInboundHandlerAdapter(){ public void channelRead(ChannelHandlerContext c, Object o){ p2.add(o);} });
    m.invoke(sp, mc);
    mc.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x10,0x00,(byte)0xfd,0x05,0x09,'l','o','c','a','l','h','o','s','t'}));
    System.out.println("MC passed=" + p2.size() + " sniffer still=" + (mc.pipeline().get("spartacube_news_http")!=null) + " out=" + mc.outboundMessages().size());
    System.exit(0);
  }
}
