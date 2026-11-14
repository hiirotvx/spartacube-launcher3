package fr.spartacube.client.hud;

import org.lwjgl.glfw.GLFW;

/** Touches configurables : lecture de l'état et nom lisible. */
public final class Keys {
    private Keys() {}

    public static boolean isDown(int key) {
        long w = GLFW.glfwGetCurrentContext();
        return w != 0L && key > 0 && GLFW.glfwGetKey(w, key) == GLFW.GLFW_PRESS;
    }

    /** Première touche enfoncée (hors Échap), ou -1. */
    public static int firstPressed() {
        long w = GLFW.glfwGetCurrentContext();
        if (w == 0L) return -1;
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            if (k == GLFW.GLFW_KEY_ESCAPE) continue;
            if (GLFW.glfwGetKey(w, k) == GLFW.GLFW_PRESS) return k;
        }
        return -1;
    }

    public static String name(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT_ALT: return "Alt gauche";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "Alt droit";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "Shift gauche";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "Shift droit";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "Ctrl gauche";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "Ctrl droit";
            case GLFW.GLFW_KEY_SPACE: return "Espace";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "Verr. Maj";
            case GLFW.GLFW_KEY_ENTER: return "Entrée";
            case GLFW.GLFW_KEY_BACKSPACE: return "Retour";
            case GLFW.GLFW_KEY_INSERT: return "Inser";
            case GLFW.GLFW_KEY_DELETE: return "Suppr";
            case GLFW.GLFW_KEY_HOME: return "Début";
            case GLFW.GLFW_KEY_END: return "Fin";
            case GLFW.GLFW_KEY_PAGE_UP: return "Page haut";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "Page bas";
            case GLFW.GLFW_KEY_UP: return "Haut";
            case GLFW.GLFW_KEY_DOWN: return "Bas";
            case GLFW.GLFW_KEY_LEFT: return "Gauche";
            case GLFW.GLFW_KEY_RIGHT: return "Droite";
            default:
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return "Pavé " + (key - GLFW.GLFW_KEY_KP_0);
                String n = GLFW.glfwGetKeyName(key, 0);
                return n != null ? n.toUpperCase(java.util.Locale.ROOT) : "Touche " + key;
        }
    }
}
