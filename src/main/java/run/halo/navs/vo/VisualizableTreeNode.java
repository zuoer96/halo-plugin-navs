package run.halo.navs.vo;

import java.util.Iterator;
import java.util.List;

/**
 * Show Tree Hierarchy.
 *
 * @author zuoer
 */
public interface VisualizableTreeNode<T extends VisualizableTreeNode<T>> {

    /**
     * Visualize tree node.
     */
    default void print(StringBuilder buffer, String prefix, String childrenPrefix) {
        buffer.append(prefix);
        buffer.append(nodeText());
        buffer.append('\n');
        if (getChildren() == null) {
            return;
        }
        for (Iterator<T> it = getChildren().iterator(); it.hasNext(); ) {
            T next = it.next();
            if (it.hasNext()) {
                next.print(buffer, childrenPrefix + "├── ", childrenPrefix + "│   ");
            } else {
                next.print(buffer, childrenPrefix + "└── ", childrenPrefix + "    ");
            }
        }
    }

    String nodeText();

    List<T> getChildren();
}
