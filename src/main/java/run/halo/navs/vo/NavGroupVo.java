package run.halo.navs.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.navs.NavGroup;

/**
 * @author zuoer
 */
@Value
@Builder
public class NavGroupVo implements ExtensionVoOperator {

    MetadataOperator metadata;

    NavGroup.NavGroupSpec spec;

    @With
    List<NavVo> navs;

    @With
    List<NavGroupVo> children;


    public static NavGroupVo from(NavGroup navGroup) {
        return NavGroupVo.builder()
            .metadata(navGroup.getMetadata())
            .spec(navGroup.getSpec())
            .navs(List.of())
            .children(List.of())
            .build();
    }


}
