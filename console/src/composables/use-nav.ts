import type { NavGroup, NavGroupList, NavList,NavGroupSpec } from "@/types";
import apiClient from "@/utils/api-client";
import { useQuery } from "@tanstack/vue-query";
import { ref, type Ref } from "vue";
import cloneDeep from "lodash.clonedeep";
import {Dialog,Toast} from "@halo-dev/components";

export function useNavFetch(
  page: Ref<number>,
  size: Ref<number>,
  keyword?: Ref<string>,
  group?: Ref<string>
) {
  const total = ref(0);

  const {
    data: navs,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["navs", page, size, group, keyword],
    queryFn: async () => {
      const { data } = await apiClient.get<NavList>(
        "/apis/api.plugin.halo.run/v1alpha1/plugins/PluginNavs/navs",
        {
          params: {
            page: page.value,
            size: size.value,
            keyword: keyword?.value,
            groupName: group?.value,
            sort: "priority,asc",
          },
        }
      );

      total.value = data.total;

      return data.items;
    },
    refetchOnWindowFocus: false,
    refetchInterval(data) {
      const deletingNavs = data?.filter(
        (nav) => !!nav.metadata.deletionTimestamp
      );
      return deletingNavs?.length ? 1000 : false;
    },
  });

  return {
    navs,
    isLoading,
    refetch,
    total,
  };
}


export function useNavGroupTreeFetch(
  page: Ref<number>,
  size: Ref<number>,
  keyword?: Ref<string>,
  group?: Ref<string>
) {
  const total = ref(0);

  const {
    data: navgroups,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["navgroups", page, size, group, keyword],
    queryFn: async () => {
      const { data } = await apiClient.get<NavList>(
        "/apis/api.plugin.halo.run/v1alpha1/plugins/PluginNavs/navgroups",
        {
          params: {
            page: page.value,
            size: size.value,
            keyword: keyword?.value,
            groupName: group?.value,
            sort: "priority,asc",
          },
        }
      );

      total.value = data.total;

      return data.items;
    },
    refetchOnWindowFocus: false,
    refetchInterval(data) {
      const deletingNavs = data?.filter(
        (nav) => !!nav.metadata.deletionTimestamp
      );
      return deletingNavs?.length ? 1000 : false;
    },
  });

  return {
    navgroups,
    isLoading,
    refetch,
    total,
  };
}

export function useNavGroupFetch() {
  const {
    data: groups,
    isLoading,
    refetch,
  } = useQuery<NavGroup[]>({
    queryKey: ["nav-groups"],
    queryFn: async () => {
      const { data } = await apiClient.get<NavGroupList>(
        "/apis/core.halo.run/v1alpha1/navgroups"
      );

      return data.items
        .map((group) => {
          if (group.spec) {
            group.spec.priority = group.spec.priority || 0;
          }
          return group;
        })
        .sort((a, b) => {
          return (a.spec?.priority || 0) - (b.spec?.priority || 0);
        });
    },
    refetchOnWindowFocus: false,
    refetchInterval(data) {
      const deletingGroups = data?.filter((group) => {
        return !!group.metadata.deletionTimestamp;
      });
      return deletingGroups?.length ? 1000 : false;
    },
  });

  return {
    groups,
    isLoading,
    refetch,
  };
}



export interface NavGroupTreeSpec extends Omit<NavGroupSpec, "children"> {
  children: NavGroupTree[];
}

export interface NavGroupTree extends Omit<NavGroup, "spec"> {
  spec: NavGroupTreeSpec;
}

export function buildNavGroupsTree(navGroups: NavGroup[]): NavGroupTree[] {
  const navGroupsToUpdate = cloneDeep(navGroups);

  const navGroupsMap = {};
  const parentMap = {};

  navGroupsToUpdate.forEach((navGroup) => {
    navGroupsMap[navGroup.metadata.name] = navGroup;
    // @ts-ignore
    navGroup.spec.children.forEach((child) => {
      parentMap[child] = navGroup.metadata.name;
    });
    // @ts-ignore
    navGroup.spec.children = [];
  });

  navGroupsToUpdate.forEach((navGroup) => {
    const parentName = parentMap[navGroup.metadata.name];
    if (parentName && navGroupsMap[parentName]) {
      navGroupsMap[parentName].spec.children.push(navGroup);
    }
  });

  const navGroupsTree = navGroupsToUpdate.filter(
    (node) => parentMap[node.metadata.name] === undefined
  );

  return sortNavGroupsTree(navGroupsTree);
}

export function sortNavGroupsTree(
  navGroupsTree: NavGroupTree[] | NavGroup[]
): NavGroupTree[] {
  return navGroupsTree
    .sort((a, b) => {
      if (a.spec.priority < b.spec.priority) {
        return -1;
      }
      if (a.spec.priority > b.spec.priority) {
        return 1;
      }
      return 0;
    })
    .map((navGroup) => {
      if (navGroup.spec.children.length) {
        return {
          ...navGroup,
          spec: {
            ...navGroup.spec,
            children: sortNavGroupsTree(navGroup.spec.children),
          },
        };
      }
      return navGroup;
    });
}

export function resetNavGroupsTreePriority(
  navGroupsTree: NavGroupTree[]
): NavGroupTree[] {
  for (let i = 0; i < navGroupsTree.length; i++) {
    navGroupsTree[i].spec.priority = i;
    if (navGroupsTree[i].spec.children) {
      resetNavGroupsTreePriority(navGroupsTree[i].spec.children);
    }
  }
  return navGroupsTree;
}

export function convertTreeToNavGroups(navGroupsTree: NavGroupTree[]) {
  const navGroups: NavGroup[] = [];
  const navGroupsMap = new Map<string, NavGroup>();
  const convertNavGroup = (node: NavGroupTree | undefined) => {
    if (!node) {
      return;
    }
    const children = node.spec.children || [];
    navGroupsMap.set(node.metadata.name, {
      ...node,
      spec: {
        ...node.spec,
        // @ts-ignore
        children: children.map((child) => child.metadata.name),
      },
    });
    children.forEach((child) => {
      convertNavGroup(child);
    });
  };
  navGroupsTree.forEach((node) => {
    convertNavGroup(node);
  });
  navGroupsMap.forEach((node) => {
    navGroups.push(node);
  });
  return navGroups;
}

export function convertNavGroupTreeToNavGroup(
  navGroupTree: NavGroupTree
): NavGroup {
  const childNames = navGroupTree.spec.children.map(
    (child) => child.metadata.name
  );
  return {
    ...navGroupTree,
    spec: {
      ...navGroupTree.spec,
      children: childNames,
    },
  };
}

export const getNavGroupPath = (
  navGroups: NavGroupTree[],
  name: string,
  path: NavGroupTree[] = []
): NavGroupTree[] | undefined => {
  for (const navGroup of navGroups) {
    if (navGroup.metadata && navGroup.metadata.name === name) {
      return path.concat([navGroup]);
    }

    if (navGroup.spec && navGroup.spec.children) {
      const found = getNavGroupPath(
        navGroup.spec.children,
        name,
        path.concat([navGroup])
      );
      if (found) {
        return found;
      }
    }
  }
};



interface usePostNavGroupReturn {
  navGroups: Ref<NavGroup[] | undefined>;
  navGroupsTree: Ref<NavGroupTree[]>;
  isLoading: Ref<boolean>;
  handleFetchNavGroups: () => void;
  handleDelete: (navGroup: NavGroupTree) => void;
}

export function usePostNavGroup(): usePostNavGroupReturn {

  const navGroupsTree = ref<NavGroupTree[]>([] as NavGroupTree[]);

  const {
    data: navGroups,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["post-navGroups"],
    queryFn: async () => {
      const { data } =
       
      await apiClient.get<NavGroupList>(
        "/apis/core.halo.run/v1alpha1/navgroups",
        {
          params: {
            page: 0,
            size: 0,
          }
        }
      );

      return data.items;
    },
    refetchInterval(data) {
      const abnormalNavGroups = data?.filter(
        (navGroup) =>
          !!navGroup.metadata.deletionTimestamp || !navGroup.status?.permalink
      );
      return abnormalNavGroups?.length ? 1000 : false;
    },
    onSuccess(data) {
      navGroupsTree.value = buildNavGroupsTree(data);
    },
  });

  const handleDelete = async (navGroup: NavGroupTree) => {
    Dialog.warning({
      title: "确定要删除该分类吗？",
      description: " 删除此分类之后，对应文章的关联将被解除。该操作不可恢复。",
      confirmType: "danger",
      confirmText: "确定",
      cancelText: "取消",
      onConfirm: async () => {
        try {
          await apiClient.delete<NavGroupList>(
            "/apis/core.halo.run/v1alpha1/navgroups",
            {
              params: {
                name: navGroup.metadata.name,
              }
            }
          );
         

          Toast.success("删除成功！");
        } catch (e) {
          console.error("Failed to delete tag", e);
        } finally {
          await refetch();
        }
      },
    });
  };

  return {
    navGroups,
    navGroupsTree,
    isLoading,
    handleFetchNavGroups: refetch,
    handleDelete,
  };
}
