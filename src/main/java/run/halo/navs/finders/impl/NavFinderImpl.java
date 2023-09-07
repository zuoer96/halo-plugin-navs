package run.halo.navs.finders.impl;

import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToPredicate;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.comparator.Comparators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.theme.finders.Finder;
import run.halo.navs.Nav;
import run.halo.navs.NavGroup;
import run.halo.navs.finders.NavFinder;
import run.halo.navs.vo.NavGroupTreeVo;
import run.halo.navs.vo.NavGroupVo;
import run.halo.navs.vo.NavVo;

/**
 * A default implementation for {@nav NavFinder}.
 *
 * @author zuoer
 */
@Finder("navFinder")
public class NavFinderImpl implements NavFinder {
    private final ReactiveExtensionClient client;

    public NavFinderImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    // ------------------ nav ----------------------
    @Override
    public Flux<NavVo> listBy(String groupName) {
        return listAll(nav -> StringUtils.equals(nav.getSpec().getGroupName(), groupName)
            && nav.getMetadata().getDeletionTimestamp() != null)
            .map(NavVo::from);
    }

    Flux<Nav> listAll(@Nullable Predicate<Nav> predicate) {
        return client.list(Nav.class, predicate, defaultNavComparator());
    }

    static Comparator<Nav> defaultNavComparator() {
        Function<Nav, Integer> priority = nav -> nav.getSpec().getPriority();
        Function<Nav, Instant> createTime = nav -> nav.getMetadata().getCreationTimestamp();
        Function<Nav, String> name = nav -> nav.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
            .thenComparing(createTime)
            .thenComparing(name);
    }

    // ------------------ navGroup ----------------------
    @Override
    public Flux<NavGroupVo> groupBy() {
        Flux<Nav> navFlux = listAll(null);
        return listAllGroups()
            .concatMap(group -> navFlux
                .filter(nav -> StringUtils.equals(nav.getSpec().getGroupName(),
                    group.getMetadata().getName())
                )
                .map(NavVo::from)
                .collectList()
                .map(group::withNavs)
                .defaultIfEmpty(group)
            )
            .mergeWith(Mono.defer(() -> ungrouped()
                .map(NavGroupVo::from)
                .flatMap(navGroup -> navFlux.filter(
                        nav -> StringUtils.isBlank(nav.getSpec().getGroupName()))
                    .map(NavVo::from)
                    .collectList()
                    .map(navGroup::withNavs)
                    .defaultIfEmpty(navGroup)
                ))
            );
    }

    Mono<NavGroup> ungrouped() {
        NavGroup navGroup = new NavGroup();
        navGroup.setMetadata(new Metadata());
        navGroup.getMetadata().setName("ungrouped");
        navGroup.setSpec(new NavGroup.NavGroupSpec());
        navGroup.getSpec().setDisplayName("");
        navGroup.getSpec().setPriority(0);
        return Mono.just(navGroup);
    }


    @Override
    public Flux<NavGroupVo> listAllGroups() {
        return client.list(NavGroup.class, null, defaultGroupComparator())
            .map(NavGroupVo::from);
    }

    @Override
    public Flux<NavGroupTreeVo> listGroupsAsTree() {
        return this.toNavGroupTreeVoFlux(null);
    }

    @Override
    public Flux<NavGroupTreeVo> listGroupsAsTree(String name) {
        return this.toNavGroupTreeVoFlux(name);
    }

    @SuppressWarnings("checkstyle:Indentation")
    @Override
    public Mono<List<NavGroupTreeVo>> listGroupsAsTree(IListRequest.QueryListRequest query) {

        return this.toNavGroupTreeVoFlux(null).collectList();
    }
    Predicate<NavGroup> navGroupListPredicate(IListRequest.QueryListRequest query) {
        return labelAndFieldSelectorToPredicate(query.getLabelSelector(),
            query.getFieldSelector()
        );
    }

    Flux<NavGroupTreeVo> toNavGroupTreeVoFlux(String name) {
        return listAllGroups()
            .collectList()
            .flatMapIterable(categoryVos -> {
                Map<String, NavGroupTreeVo> nameIdentityMap = categoryVos.stream()
                    .map(NavGroupTreeVo::from)
                    .collect(Collectors.toMap(categoryVo -> categoryVo.getMetadata().getName(),
                        Function.identity()));

                nameIdentityMap.forEach((nameKey, value) -> {
                    // todo:linkedHashSet
                    List<String> children = value.getSpec().getChildren();
                    if (children == null) {
                        return;
                    }
                    for (String child : children) {
                        NavGroupTreeVo childNode = nameIdentityMap.get(child);
                        if (childNode != null) {
                            childNode.setParentName(nameKey);
                        }
                    }
                });
                return listToTree(nameIdentityMap.values(), name);
            });
    }

    static List<NavGroupTreeVo> listToTree(Collection<NavGroupTreeVo> list, String name) {
        Map<String, List<NavGroupTreeVo>> parentNameIdentityMap = list.stream()
            .filter(navGroupTreeVo -> navGroupTreeVo.getParentName() != null)
            .collect(Collectors.groupingBy(NavGroupTreeVo::getParentName));

        list.forEach(node -> {
            // sort children
            List<NavGroupTreeVo> children =
                parentNameIdentityMap.getOrDefault(node.getMetadata().getName(), List.of())
                    .stream()
                    .sorted(defaultTreeNodeComparator())
                    .toList();
            node.setChildren(children);
        });
        return list.stream()
            .filter(v -> StringUtils.isEmpty(name) ? v.getParentName() == null
                : StringUtils.equals(v.getMetadata().getName(), name))
            .sorted(defaultTreeNodeComparator())
            .collect(Collectors.toList());
    }

    static Comparator<NavGroupTreeVo> defaultTreeNodeComparator() {
        Function<NavGroupTreeVo, Integer> priority = navGroup -> navGroup.getSpec().getPriority();
        Function<NavGroupTreeVo, Instant> createTime = navGroup -> navGroup.getMetadata().getCreationTimestamp();
        Function<NavGroupTreeVo, String> name = navGroup -> navGroup.getMetadata().getName();

        return Comparator.comparing(priority)
            .thenComparing(createTime, Comparators.nullsLow())
            .thenComparing(name);
    }

    static Comparator<NavGroup> defaultGroupComparator() {
        Function<NavGroup, Integer> priority = group -> group.getSpec().getPriority();
        Function<NavGroup, Instant> createTime =
            group -> group.getMetadata().getCreationTimestamp();
        Function<NavGroup, String> name = group -> group.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
            .thenComparing(createTime)
            .thenComparing(name);
    }


}
