package com.gimlism.translucent;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.util.List;
import java.util.StringJoiner;

/**
 * Builds {@code docs/index.html}, the published site's front door.
 *
 * <p>Generated rather than hand-written because it is the one student-facing file that would
 * otherwise escape the build. Every page under {@code docs/viz/} is pinned to its generator, so a
 * hand-maintained index could list five pages while six existed and nothing in the suite would
 * notice — the exact staleness this repo's goldens exist to prevent, on the file that turns the
 * site on. {@code SiteIndexTest} closes the other direction: no file on disk missing from here.
 *
 * <p>Guide links are absolute GitHub URLs on purpose. {@code docs/.nojekyll} means Pages serves
 * {@code .md} as raw text — a browser shows plain source or offers a download — while GitHub
 * renders it with heading anchors for free. Being absolute, they also behave identically whether
 * this page was opened from the site or from {@code file://} off a clone, which is the same reason
 * the visualisation links stay relative.
 */
final class SiteIndex {

    private SiteIndex() { }

    private static final String TEMPLATE = "/web/site-index.html";
    private static final String PAGES_TOKEN = "<!--__PAGES__-->";

    /** Where a guide under {@code docs/} is rendered, since Pages cannot render it itself. */
    static final String GUIDE_BASE = "https://github.com/gimlism/translucent/blob/main/docs/";

    /** Render {@code pages} into the template, in the order given. */
    static String build(List<RegenerateDocs.Page> pages) {
        var body = new StringJoiner("\n");

        body.add("  <h2>Watch one — nothing to install</h2>");
        body.add("  <ul>");
        for (RegenerateDocs.Page page : pages) {
            body.add(card(page.path(), page.title(), page.blurb()));
        }
        body.add("  </ul>");

        body.add("  <h2>What each method makes you see</h2>");
        body.add("  <ul>");
        for (RegenerateDocs.Page page : pages) {
            if (page.guide() != null) {
                body.add(card(GUIDE_BASE + page.guide(), page.title(),
                        "Every narrating method, and the events it emits."));
            }
        }
        body.add("  </ul>");

        return WebVizTemplate.injectToken(TEMPLATE, PAGES_TOKEN, body.toString());
    }

    private static String card(String href, String title, String blurb) {
        return "    <li><a class=\"card\" href=\"" + esc(href) + "\">"
                + "<div class=\"t\">" + esc(title) + "</div>"
                + "<div class=\"b\">" + esc(blurb) + "</div></a></li>";
    }

    private static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
