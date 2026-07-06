package com.gimlism.translucent.hashmap.demo;

import java.awt.Desktop;
import java.net.URI;

/** Best-effort "open this URL in the default browser"; a silent no-op (prints instead) when unavailable. */
final class BrowserLauncher {

    private BrowserLauncher() {}

    static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception headlessOrUnsupported) {
            // fall through to the printed hint
        }
        System.out.println("Open " + url + " in your browser.");
    }
}
