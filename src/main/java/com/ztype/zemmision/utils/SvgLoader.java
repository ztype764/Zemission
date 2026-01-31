package com.ztype.zemmision.utils;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SvgLoader {

    public static Node loadSvg(String resourcePath, double size) {
        try (InputStream is = SvgLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return new Group();
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            Element root = doc.getDocumentElement();

            Group group = new Group();
            List<Shape> shapes = new ArrayList<>();

            parseElement(root, shapes);

            group.getChildren().addAll(shapes);

            // Default size is usually 24 based on viewBox
            double defaultSize = 24.0;
            if (root.hasAttribute("viewBox")) {
                String[] parts = root.getAttribute("viewBox").split(" ");
                if (parts.length == 4) {
                    defaultSize = Double.parseDouble(parts[3]);
                }
            }

            double scale = size / defaultSize;
            group.setScaleX(scale);
            group.setScaleY(scale);

            // Fix bounds after scaling is tricky with Group, ensuring it takes space
            // Often better to wrap in a Pane if layout is needed, but Group is fine for
            // Graphic.
            return group;

        } catch (Exception e) {
            e.printStackTrace();
            return new Group();
        }
    }

    private static void parseElement(Element element, List<Shape> shapes) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element child = (Element) children.item(i);
                Shape shape = null;

                switch (child.getTagName()) {
                    case "path":
                        shape = createPath(child);
                        break;
                    case "rect":
                        shape = createRect(child);
                        break;
                    case "circle":
                        shape = createCircle(child);
                        break;
                    case "line":
                        shape = createLine(child);
                        break;
                    case "polyline":
                        shape = createPolyline(child);
                        break;
                    case "polygon":
                        shape = createPolygon(child);
                        break;
                    case "g":
                        parseElement(child, shapes);
                        break;
                }

                if (shape != null) {
                    applyStyles(child, shape);
                    shapes.add(shape);
                }
            }
        }
    }

    private static void applyStyles(Element element, Shape shape) {
        String stroke = element.getAttribute("stroke");
        if (stroke != null && !stroke.isEmpty()) {
            if ("currentColor".equals(stroke)) {
                // Approximate currentColor with black or inherit.
                // For actual currentColor support, we'd need binding, which is complex here.
                // We'll default to style class handling or black.
                shape.setStroke(Color.BLACK);
                shape.getStyleClass().add("icon-stroke"); // Allow CSS to override
            } else {
                shape.setStroke(Color.web(stroke));
            }
        }

        String fill = element.getAttribute("fill");
        if ("none".equals(fill)) {
            shape.setFill(Color.TRANSPARENT);
        } else if (fill != null && !fill.isEmpty()) {
            if ("currentColor".equals(fill)) {
                shape.setFill(Color.BLACK);
                shape.getStyleClass().add("icon-fill");
            } else {
                shape.setFill(Color.web(fill));
            }
        } else {
            // Default fill behavior if not specified?
            shape.setFill(Color.TRANSPARENT);
        }

        String strokeWidth = element.getAttribute("stroke-width");
        if (strokeWidth != null && !strokeWidth.isEmpty()) {
            try {
                shape.setStrokeWidth(Double.parseDouble(strokeWidth));
            } catch (NumberFormatException ignored) {
            }
        }

        // Inherit from parent if needed? XML doesn't cascade automatically here like
        // CSS,
        // but for these simple SVGs, attributes are usually on the element or root.
        // We might need to check root for generic stroke.
        Element parent = (Element) element.getParentNode();
        if ((shape.getStroke() == null || shape.getStroke() == Color.TRANSPARENT) && parent.hasAttribute("stroke")) {
            String pStroke = parent.getAttribute("stroke");
            if ("currentColor".equals(pStroke)) {
                shape.setStroke(Color.BLACK);
                shape.getStyleClass().add("icon-stroke");
            } else {
                shape.setStroke(Color.web(pStroke));
            }
        }
        if (parent.hasAttribute("stroke-width") && shape.getStrokeWidth() == 1.0) { // Default is 1.0
            try {
                shape.setStrokeWidth(Double.parseDouble(parent.getAttribute("stroke-width")));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static SVGPath createPath(Element element) {
        SVGPath path = new SVGPath();
        path.setContent(element.getAttribute("d"));
        return path;
    }

    private static SVGPath createRect(Element element) {
        double x = Double.parseDouble(element.getAttribute("x"));
        double y = Double.parseDouble(element.getAttribute("y"));
        double w = Double.parseDouble(element.getAttribute("width"));
        double h = Double.parseDouble(element.getAttribute("height"));

        SVGPath path = new SVGPath();
        // M x y h w v h z
        String d = String.format("M %f %f H %f V %f H %f Z", x, y, x + w, y + h, x);
        path.setContent(d);
        return path;
    }

    private static SVGPath createCircle(Element element) {
        double cx = Double.parseDouble(element.getAttribute("cx"));
        double cy = Double.parseDouble(element.getAttribute("cy"));
        double r = Double.parseDouble(element.getAttribute("r"));

        SVGPath path = new SVGPath();
        // M cx-r cy a r r 0 1 0 2r 0 a r r 0 1 0 -2r 0
        String d = String.format("M %f %f a %f %f 0 1 0 %f 0 a %f %f 0 1 0 %f 0",
                cx - r, cy, r, r, 2 * r, r, r, -2 * r);
        path.setContent(d);
        return path;
    }

    private static SVGPath createLine(Element element) {
        double x1 = Double.parseDouble(element.getAttribute("x1"));
        double y1 = Double.parseDouble(element.getAttribute("y1"));
        double x2 = Double.parseDouble(element.getAttribute("x2"));
        double y2 = Double.parseDouble(element.getAttribute("y2"));

        SVGPath path = new SVGPath();
        path.setContent(String.format("M %f %f L %f %f", x1, y1, x2, y2));
        return path;
    }

    private static SVGPath createPolyline(Element element) {
        String points = element.getAttribute("points");
        SVGPath path = new SVGPath();
        path.setContent("M " + points.replaceFirst(" ", " L ")); // Naive replacement, works for "x y x y" but better
                                                                 // split

        // Better parser
        String[] p = points.trim().split("[\\s,]+");
        StringBuilder sb = new StringBuilder();
        if (p.length >= 2) {
            sb.append("M ").append(p[0]).append(" ").append(p[1]);
            for (int i = 2; i < p.length; i += 2) {
                sb.append(" L ").append(p[i]).append(" ").append(p[i + 1]);
            }
        }
        path.setContent(sb.toString());
        return path;
    }

    private static SVGPath createPolygon(Element element) {
        SVGPath path = createPolyline(element);
        path.setContent(path.getContent() + " Z");
        return path;
    }
}
