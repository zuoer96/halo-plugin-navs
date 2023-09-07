package run.halo.navs;

import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;

/**
 * @author zuoer
 */
@Component
public class NavPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public NavPlugin(PluginWrapper wrapper, SchemeManager schemeManager) {
        super(wrapper);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        // 插件启动时注册自定义模型
        schemeManager.register(Nav.class);
        schemeManager.register(NavGroup.class);
        System.out.println("===>导航插件启动成功！");
    }

    @Override
    public void stop() {
        // 插件停用时取消注册自定义模型
        schemeManager.unregister(schemeManager.get(Nav.class));
        schemeManager.unregister(schemeManager.get(NavGroup.class));
        System.out.println("===>导航插件停止！");
    }
}
