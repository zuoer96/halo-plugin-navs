package run.halo.navs.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.util.Assert;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.navs.NavGroup;

/**
 * A tree vo for {@link NavGroup}.
 *
 * @author guqing
 * @since 2.0.0
 */
@Data
@Builder
@ToString
@EqualsAndHashCode
public class NavGroupTreeVo implements VisualizableTreeNode<NavGroupTreeVo>, ExtensionVoOperator {

    private MetadataOperator metadata;

    private NavGroup.NavGroupSpec spec;

    private List<NavGroupTreeVo> children;

    private String parentName;


    /**
     * Convert {@link NavGroupVo} to {@link NavGroupTreeVo}.
     *
     * @param navGroup navGroup value object
     * @return navGroup tree value object
     */
    public static NavGroupTreeVo from(NavGroupVo navGroup) {
        Assert.notNull(navGroup, "The navGroup must not be null");
        return NavGroupTreeVo.builder()
            .metadata(navGroup.getMetadata())
            .spec(navGroup.getSpec())
            .children(List.of())
            .build();
    }

    @Override
    public String nodeText() {
        return getSpec().getDisplayName();
    }
}