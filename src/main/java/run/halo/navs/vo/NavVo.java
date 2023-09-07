package run.halo.navs.vo;

import lombok.Builder;
import lombok.Value;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.navs.Nav;

/**
 * @author zuoer
 */
@Value
@Builder
public class NavVo implements ExtensionVoOperator {

    MetadataOperator metadata;

    Nav.NavSpec spec;

    public static NavVo from(Nav nav) {
        return NavVo.builder()
            .metadata(nav.getMetadata())
            .spec(nav.getSpec())
            .build();
    }
}
