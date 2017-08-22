package ui.window;

import com.hp.gagawa.java.Document;
import com.hp.gagawa.java.DocumentType;
import com.hp.gagawa.java.elements.B;
import com.hp.gagawa.java.elements.Font;
import com.hp.gagawa.java.elements.P;
import com.hp.gagawa.java.elements.Text;

public class HtmlReportComposer {

    private static final String PAGE_BACKGROUND_COLOR = "#000000";
    private static final String PAGE_MARGIN = "7px";
    private static final String LINE_BOTTOM_PADDING = "0px";
    private static final String FONT_SIZE = "5";
    private static final String FONT_TYPE = "monospace";
    private final Document doc;

    public HtmlReportComposer() {
        doc = new Document(DocumentType.XHTMLStrict);
        doc.body.setBgcolor(PAGE_BACKGROUND_COLOR);
        doc.body.setStyle("margin:" + PAGE_MARGIN);
    }

    public HtmlReportComposer addLine(ReportMessageType type, String format, Object... args) {
        Text textNode = new Text(String.format(format, args));
        Font fontNode = new Font();
        P paragraphNode = new P();
        B boldNode = null;

        if (type.bold) {
            boldNode = new B();
            boldNode.appendChild(textNode);
        }

        fontNode.setSize(FONT_SIZE).setColor(type.color).setFace(FONT_TYPE).appendChild(boldNode == null ? textNode : boldNode);
        paragraphNode.setStyle("margin:0px;padding-bottom:" + LINE_BOTTOM_PADDING).appendChild(fontNode);

        doc.body.appendChild(paragraphNode);
        return this;
    }

    public String compose() {
        return doc.write();
    }
}
