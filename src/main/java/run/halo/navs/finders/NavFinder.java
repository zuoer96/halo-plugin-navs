package run.halo.navs.finders;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.router.IListRequest;
import run.halo.navs.vo.NavGroupTreeVo;
import run.halo.navs.vo.NavGroupVo;
import run.halo.navs.vo.NavVo;

/**
 * A finder for {@nav run.halo.navs.Nav}.
 *
 * @author zuoer
 */
public interface NavFinder {

    Flux<NavVo> listBy(String group);

    Flux<NavGroupVo> groupBy();

    Flux<NavGroupVo> listAllGroups();

    Flux<NavGroupTreeVo> listGroupsAsTree();

    Flux<NavGroupTreeVo> listGroupsAsTree(String name);

    Mono<List<NavGroupTreeVo>> listGroupsAsTree(IListRequest.QueryListRequest request);
}
