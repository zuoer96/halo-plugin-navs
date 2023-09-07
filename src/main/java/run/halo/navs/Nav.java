package run.halo.navs;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * @author zuoer
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "core.halo.run", version = "v1alpha1",
        kind = "Nav", plural = "navs", singular = "nav")
public class Nav extends AbstractExtension {

    private NavSpec spec;

    @Data
    public static class NavSpec {
        @Schema(required = true)
        private String url;

        @Schema(required = true)
        private String displayName;

        private String logo;

        private String description;

        private Integer priority;

        private String groupName;
    }
}
