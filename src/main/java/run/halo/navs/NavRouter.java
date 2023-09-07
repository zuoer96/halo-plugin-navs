package run.halo.navs;

import static java.util.Comparator.comparing;
import static run.halo.app.extension.router.QueryParamBuildUtil.buildParametersFromType;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToPredicate;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.util.comparator.Comparators;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.SortResolver;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.IListRequest;
import run.halo.navs.finders.NavFinder;
import run.halo.navs.vo.NavGroupTreeVo;
import run.halo.navs.vo.NavGroupVo;

@Configuration
@RequiredArgsConstructor
public class NavRouter {

    private final NavFinder navFinder;
    private final ReactiveExtensionClient client;
    private final String tag = "api.plugin.halo.run/v1alpha1/Nav";

    // ---------------- nav ------------------
    @Bean
    RouterFunction<ServerResponse> navTemplateRoute() {
        return RouterFunctions.route()
            .GET("/navs",request -> ServerResponse.ok().render("navs", Map.of("groups", navGroups())))
            .GET("/navgroups",request -> ServerResponse.ok().render("navgroups", Map.of("navgroups", navGroupGroups())))
            .build();


    }

    @Bean
    RouterFunction<ServerResponse> navRoute() {
        return SpringdocRouteBuilder.route()
            .nest(RequestPredicates.path("/apis/api.plugin.halo.run/v1alpha1/plugins/PluginNavs"),
                this::nested,
                builder -> builder.operationId("PluginNavsEndpoints")
                    .description("Plugin navs Endpoints").tag(tag)
            )
            .build();
    }

    RouterFunction<ServerResponse> nested() {
        return SpringdocRouteBuilder.route()
            .GET("/navs", this::listNavByGroup,
                builder -> {
                    builder.operationId("listNavs")
                        .description("Lists nav by query parameters")
                        .tag(tag);
                    buildParametersFromType(builder, NavQuery.class);
                }
            )
            .GET("/navgroups", this::listNavGroupByGroup,
                builder -> {
                    builder.operationId("listNavGroups")
                        .description("Lists navGroup by query parameters")
                        .tag(tag);
                    buildParametersFromType(builder, NavGroupQuery.class);
                }
            )
            .build();
    }

    Mono<ServerResponse> listNavByGroup(ServerRequest request) {
        NavQuery navQuery = new NavQuery(request.exchange());
        return listNav(navQuery)
            .flatMap(navs -> ServerResponse.ok().bodyValue(navs));
    }

    private Mono<ListResult<Nav>> listNav(NavQuery query) {
        return client.list(Nav.class, query.toPredicate(),
            query.toComparator(),
            query.getPage(),
            query.getSize()
        );
    }

    static class NavQuery extends IListRequest.QueryListRequest {
        private final ServerWebExchange exchange;

        public NavQuery(ServerWebExchange exchange) {
            super(exchange.getRequest().getQueryParams());
            this.exchange = exchange;
        }

        @Schema(description = "Keyword to search navs under the group")
        public String getKeyword() {
            return queryParams.getFirst("keyword");
        }

        @Schema(description = "Nav group name")
        public String getGroupName() {
            return queryParams.getFirst("groupName");
        }

        @ArraySchema(uniqueItems = true,
            arraySchema = @Schema(name = "sort",
                description = "Sort property and direction of the list result. Supported fields: "
                    + "creationTimestamp, priority"),
            schema = @Schema(description = "like field,asc or field,desc",
                implementation = String.class,
                example = "creationTimestamp,desc"))
        public Sort getSort() {
            return SortResolver.defaultInstance.resolve(exchange);
        }

        public Predicate<Nav> toPredicate() {
            Predicate<Nav> keywordPredicate = nav -> {
                var keyword = getKeyword();
                if (StringUtils.isBlank(keyword)) {
                    return true;
                }
                String keywordToSearch = keyword.trim().toLowerCase();
                return StringUtils.containsAnyIgnoreCase(nav.getSpec().getDisplayName(),
                    keywordToSearch)
                    || StringUtils.containsAnyIgnoreCase(nav.getSpec().getDescription(),
                    keywordToSearch)
                    || StringUtils.containsAnyIgnoreCase(nav.getSpec().getUrl(), keywordToSearch);
            };
            Predicate<Nav> groupPredicate = nav -> {
                var groupName = getGroupName();
                if (StringUtils.isBlank(groupName)) {
                    return true;
                }
                return StringUtils.equals(groupName, nav.getSpec().getGroupName());
            };
            Predicate<Extension> labelAndFieldSelectorToPredicate =
                labelAndFieldSelectorToPredicate(getLabelSelector(), getFieldSelector());
            return groupPredicate.and(keywordPredicate).and(labelAndFieldSelectorToPredicate);
        }

        public Comparator<Nav> toComparator() {
            var sort = getSort();
            var ctOrder = sort.getOrderFor("creationTimestamp");
            var priorityOrder = sort.getOrderFor("priority");
            List<Comparator<Nav>> comparators = new ArrayList<>();
            if (ctOrder != null) {
                Comparator<Nav> comparator =
                    comparing(nav -> nav.getMetadata().getCreationTimestamp());
                if (ctOrder.isDescending()) {
                    comparator = comparator.reversed();
                }
                comparators.add(comparator);
            }
            if (priorityOrder != null) {
                Comparator<Nav> comparator =
                    comparing(nav -> nav.getSpec().getPriority(),
                        Comparators.nullsLow());
                if (priorityOrder.isDescending()) {
                    comparator = comparator.reversed();
                }
                comparators.add(comparator);
            }
            comparators.add(compareCreationTimestamp(false));
            comparators.add(compareName(true));
            return comparators.stream()
                .reduce(Comparator::thenComparing)
                .orElse(null);
        }

        public static <E extends Extension> Comparator<E> compareCreationTimestamp(boolean asc) {
            var comparator =
                Comparator.<E, Instant>comparing(e -> e.getMetadata().getCreationTimestamp());
            return asc ? comparator : comparator.reversed();
        }

        public static <E extends Extension> Comparator<E> compareName(boolean asc) {
            var comparator = Comparator.<E, String>comparing(e -> e.getMetadata().getName());
            return asc ? comparator : comparator.reversed();
        }
    }

    private Mono<List<NavGroupVo>> navGroups() {
        return navFinder.groupBy()
            .collectList();
    }


    Mono<ServerResponse> listNavGroupByGroup(ServerRequest request) {
        NavGroupQuery navQuery = new NavGroupQuery(request.exchange());
        // return listNavGroup(navQuery).flatMap(navs -> ServerResponse.ok().bodyValue(navs));
        return navGroupGroups().flatMap(navs -> ServerResponse.ok().bodyValue(navs));
    }

    private Mono<ListResult<NavGroup>> listNavGroup(NavGroupQuery query) {
        return client.list(NavGroup.class, query.toPredicate(),
            query.toComparator(),
            query.getPage(),
            query.getSize()
        );
    }

    static class NavGroupQuery extends IListRequest.QueryListRequest {
        private final ServerWebExchange exchange;

        public NavGroupQuery(ServerWebExchange exchange) {
            super(exchange.getRequest().getQueryParams());
            this.exchange = exchange;
        }

        @Schema(description = "Keyword to search navs under the group")
        public String getKeyword() {
            return queryParams.getFirst("keyword");
        }

        @Schema(description = "NavGroup group name")
        public String getGroupName() {
            return queryParams.getFirst("groupName");
        }

        @ArraySchema(uniqueItems = true,
            arraySchema = @Schema(name = "sort",
                description = "Sort property and direction of the list result. Supported fields: "
                    + "creationTimestamp, priority"),
            schema = @Schema(description = "like field,asc or field,desc",
                implementation = String.class,
                example = "creationTimestamp,desc"))
        public Sort getSort() {
            return SortResolver.defaultInstance.resolve(exchange);
        }

        public Predicate<NavGroup> toPredicate() {
            Predicate<NavGroup> keywordPredicate = nav -> {
                var keyword = getKeyword();
                if (StringUtils.isBlank(keyword)) {
                    return true;
                }
                String keywordToSearch = keyword.trim().toLowerCase();
                return StringUtils.containsAnyIgnoreCase(nav.getSpec().getDisplayName(),
                    keywordToSearch);
            };
            Predicate<Extension> labelAndFieldSelectorToPredicate =
                labelAndFieldSelectorToPredicate(getLabelSelector(), getFieldSelector());
            return keywordPredicate.and(keywordPredicate).and(labelAndFieldSelectorToPredicate);
        }

        public Comparator<NavGroup> toComparator() {
            var sort = getSort();
            var ctOrder = sort.getOrderFor("creationTimestamp");
            var priorityOrder = sort.getOrderFor("priority");
            List<Comparator<NavGroup>> comparators = new ArrayList<>();
            if (ctOrder != null) {
                Comparator<NavGroup> comparator =
                    comparing(nav -> nav.getMetadata().getCreationTimestamp());
                if (ctOrder.isDescending()) {
                    comparator = comparator.reversed();
                }
                comparators.add(comparator);
            }
            if (priorityOrder != null) {
                Comparator<NavGroup> comparator =
                    comparing(nav -> nav.getSpec().getPriority(),
                        Comparators.nullsLow());
                if (priorityOrder.isDescending()) {
                    comparator = comparator.reversed();
                }
                comparators.add(comparator);
            }
            comparators.add(compareCreationTimestamp(false));
            comparators.add(compareName(true));
            return comparators.stream()
                .reduce(Comparator::thenComparing)
                .orElse(null);
        }

        public static <E extends Extension> Comparator<E> compareCreationTimestamp(boolean asc) {
            var comparator =
                Comparator.<E, Instant>comparing(e -> e.getMetadata().getCreationTimestamp());
            return asc ? comparator : comparator.reversed();
        }

        public static <E extends Extension> Comparator<E> compareName(boolean asc) {
            var comparator = Comparator.<E, String>comparing(e -> e.getMetadata().getName());
            return asc ? comparator : comparator.reversed();
        }
    }

    private Mono<List<NavGroupTreeVo>> navGroupGroups() {
        return navFinder.listGroupsAsTree()
            .collectList();
    }
}
