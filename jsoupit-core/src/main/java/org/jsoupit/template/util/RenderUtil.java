package org.jsoupit.template.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.jsoupit.Configuration;
import org.jsoupit.Context;
import org.jsoupit.template.TemplateException;
import org.jsoupit.template.extnode.ExtNodeConstants;
import org.jsoupit.template.render.GoThroughRenderer;
import org.jsoupit.template.render.Renderer;
import org.jsoupit.template.snippet.SnippetInvokeException;
import org.jsoupit.template.snippet.SnippetInvoker;
import org.jsoupit.template.snippet.SnippetNotResovlableException;
import org.jsoupit.template.transformer.Transformer;

/**
 * 
 * This class is a functions holder which supply the ability of applying
 * rendereres to certain Element.
 * 
 * @author e-ryu
 * 
 */
// TODO as quick implementation, I think it is necessary to rewrite the whole
// class.
public class RenderUtil {

    public final static void applySnippets(Element elem) throws SnippetNotResovlableException, SnippetInvokeException, TemplateException {
        Document doc = elem.ownerDocument();
        // we believe the doc would not be null, but if at somewhere a
        // hung element is passed, it will be a problem.
        if (doc == null) {
            throw new SnippetInvokeException("A hung element without owner document cannot be applied to execute snippets.");
        }
        String selector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR, ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS,
                ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_READY);
        List<Element> snippetList = new ArrayList<>(elem.select(selector));
        int snippetListCount = snippetList.size();
        for (int i = snippetListCount - 1; i >= 0; i--) {
            // if parent snippet has not been executed, the current snippet will
            // not be executed too.
            if (isBlockedByParentSnippet(doc, snippetList.get(i))) {
                snippetList.remove(i);
            }
        }

        String renderDeclaration;
        Renderer renderer;
        Context context = Context.getCurrentThreadContext();
        Configuration conf = context.getConfiguration();
        SnippetInvoker invoker = conf.getSnippetInvoker();

        String refId;
        Element renderTarget;
        for (Element element : snippetList) {
            if (element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_TYPE).equals(ExtNodeConstants.SNIPPET_NODE_ATTR_TYPE_FAKE)) {
                renderTarget = element.children().first();
            } else {
                renderTarget = element;
            }
            context.setCurrentRenderingElement(renderTarget);
            renderDeclaration = element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_RENDER);
            renderer = invoker.invoke(renderDeclaration);
            refId = element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_REFID);
            apply(renderTarget, renderer);
            if (element.ownerDocument() == null) {
                // it means this snippet element is replaced by a element
                // completely
                String reSelector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR, ExtNodeConstants.SNIPPET_NODE_ATTR_REFID,
                        refId);
                Elements elems = doc.select(reSelector);
                if (elems.size() > 0) {
                    element = elems.get(0);
                } else {
                    element = null;
                }
            }

            if (element != null) {
                element.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS, ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_FINISHED);
            }
            context.setCurrentRenderingElement(null);
        }

        // load embed nodes which parents blocking parents has finished
        List<Element> embedNodeList = elem.select(ExtNodeConstants.EMBED_NODE_TAG_SELECTOR);
        int embedNodeListCount = embedNodeList.size();
        Iterator<Element> embedNodeIterator = embedNodeList.iterator();
        Element embed;
        Element embedContent;
        while (embedNodeIterator.hasNext()) {
            embed = embedNodeIterator.next();
            if (isBlockedByParentSnippet(doc, embed)) {
                continue;
            }
            embedContent = TemplateUtil.getEmbedNodeContent(embed);
            embed.before(embedContent);
            embed.remove();
        }

        if ((snippetListCount + embedNodeListCount) > 0) {
            TemplateUtil.regulateElement(elem);
            applySnippets(elem);
        }
    }

    private final static boolean isBlockedByParentSnippet(Document doc, Element elem) {
        boolean isBlocked;
        String blockingId = elem.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_BLOCK);
        if (blockingId.isEmpty()) {
            // empty block id means there is no parent snippet that need to be
            // aware
            isBlocked = false;
        } else {
            String parentSelector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR, ExtNodeConstants.SNIPPET_NODE_ATTR_REFID,
                    blockingId);
            Element parentSnippet = doc.select(parentSelector).get(0);
            if (parentSnippet.attr(ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS).equals(ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_FINISHED)) {
                isBlocked = false;
            } else {
                isBlocked = true;
            }
        }
        return isBlocked;
    }

    public final static void apply(Element target, Renderer renderer) {
        Document doc = target.ownerDocument();
        Renderer currentRenderer = renderer;
        String selector;
        ListIterator<Element> elemIterator;
        Element elem;
        while (currentRenderer != null) {
            if (currentRenderer instanceof GoThroughRenderer) {
                currentRenderer = currentRenderer.getNext();
                continue;
            }

            selector = currentRenderer.getSelector();
            Elements targets = target.select(selector);
            elemIterator = targets.listIterator();
            while (elemIterator.hasNext()) {
                elem = elemIterator.next();
                List<Transformer<?>> transformerList = currentRenderer.getTransformerList();
                Node resultNode;
                for (Transformer<?> transformer : transformerList) {
                    resultNode = transformer.invoke(elem);
                    elem.before(resultNode);
                }// for
                elem.remove();
            }// while elemIterator
            currentRenderer = currentRenderer.getNext();
        }// while currentRenderer.next
    }

    public final static void applyClearAction(Element target) {
        String removeSnippetSelector = SelectorUtil.attr(ExtNodeConstants.SNIPPET_NODE_TAG_SELECTOR,
                ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS, ExtNodeConstants.SNIPPET_NODE_ATTR_STATUS_FINISHED);

        removeJsoupitNodes(target, removeSnippetSelector, true);
        removeJsoupitNodes(target, ExtNodeConstants.CLEAR_NODE_TAG_SELECTOR, false);
        removeJsoupitNodes(target, ExtNodeConstants.GROUP_NODE_TAG_SELECTOR, true);

    }

    public final static void removeJsoupitNodes(Element target, String selector, boolean pullupChildren) {
        Elements removeNodes = target.select(selector);
        Iterator<Element> it = removeNodes.iterator();
        Element rm;
        while (it.hasNext()) {
            rm = it.next();
            if (rm.ownerDocument() == null) {
                continue;
            }
            if (pullupChildren) {
                pullupChildren(rm);
            }
            rm.remove();
        }
    }

    public final static void pullupChildren(Element elem) {
        List<Node> childrenNodes = new ArrayList<>(elem.childNodes());
        for (Node node : childrenNodes) {
            node.remove();
            elem.before(node);
        }
    }
}
