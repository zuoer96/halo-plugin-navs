package run.halo.navs;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashSet;
import java.util.List;
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
    kind = "NavGroup", plural = "navgroups", singular = "navgroup")
public class NavGroup extends AbstractExtension {

    private NavGroupSpec spec;

    @Data
    public static class NavGroupSpec {
        @Schema(required = true)
        private String displayName;

        private Integer priority;

        @Deprecated(since = "1.2.0", forRemoval = true)
        @Schema(description = "Names of navs below this group.")
        @ArraySchema(arraySchema = @Schema(description = "Navs of this group."), schema = @Schema(description = "Name of nav."))
        private LinkedHashSet<String> navs;

        private List<String> children;
    }
}
