<script lang="ts" setup>
import { provide, ref, watch, type Ref } from "vue";
import Draggable from "vuedraggable";
import {
  IconList,
  IconArrowLeft,
  IconArrowRight,
  VButton,
  VCard,
  VPageHeader,
  VPagination,
  VSpace,
  VEntity,
  VEntityField,
  VAvatar,
  VStatusDot,
  Dialog,
  VEmpty,
  IconAddCircle,
  VLoading,
  VDropdown,
  VDropdownItem,
  VDropdownDivider,
  Toast,
} from "@halo-dev/components";
import GroupList from "../components/GroupList.vue";
import NavEditingModal from "../components/NavEditingModal.vue";
import apiClient from "@/utils/api-client";
import type { Nav, NavGroup } from "@/types";
import yaml from "yaml";
import { useFileSystemAccess } from "@vueuse/core";
import { formatDatetime } from "@/utils/date";
import { useQueryClient } from "@tanstack/vue-query";
import { useRouteQuery } from "@vueuse/router";
import { useNavFetch, useNavGroupFetch } from "@/composables/use-nav";
import cloneDeep from "lodash.clonedeep";

const queryClient = useQueryClient();

const drag = ref(false);
const selectedNav = ref<Nav | undefined>();
const selectedNavs = ref<string[]>([]);
const editingModal = ref(false);
const checkedAll = ref(false);

const groupQuery = useRouteQuery<string>("group");
provide<Ref<string>>("groupQuery", groupQuery);

const page = ref(1);
const size = ref(20);
const keyword = ref("");

const { navs, isLoading, total, refetch } = useNavFetch(
  page,
  size,
  keyword,
  groupQuery
);
const draggableNavs = ref<Nav[]>();

watch(
  () => navs.value,
  () => {
    draggableNavs.value = cloneDeep(navs.value);
  },
  {
    immediate: true,
  }
);

watch(
  () => groupQuery.value,
  () => {
    page.value = 1;
    selectedNavs.value.length = 0;
    checkedAll.value = false;
  }
);

function onKeywordChange(data: { keyword: string }) {
  keyword.value = data.keyword;
  page.value = 1;
}

const handleSelectPrevious = () => {
  if (!navs.value) {
    return;
  }

  const currentIndex = navs.value.findIndex(
    (nav) => nav.metadata.name === selectedNav.value?.metadata.name
  );

  if (currentIndex > 0) {
    selectedNav.value = navs.value[currentIndex - 1];
    return;
  }

  if (currentIndex <= 0) {
    selectedNav.value = undefined;
  }
};

const handleSelectNext = () => {
  if (!navs.value) return;

  if (!selectedNav.value) {
    selectedNav.value = navs.value[0];
    return;
  }
  const currentIndex = navs.value.findIndex(
    (nav) => nav.metadata.name === selectedNav.value?.metadata.name
  );
  if (currentIndex !== navs.value.length - 1) {
    selectedNav.value = navs.value[currentIndex + 1];
  }
};

const handleOpenCreateModal = (nav: Nav) => {
  selectedNav.value = nav;
  editingModal.value = true;
};

const onPriorityChange = async () => {
  try {
    const promises = draggableNavs.value?.map((nav: Nav, index) => {
      if (nav.spec) {
        nav.spec.priority = index;
      }
      return apiClient.put(
        `/apis/core.halo.run/v1alpha1/navs/${nav.metadata.name}`,
        nav
      );
    });
    if (promises) {
      await Promise.all(promises);
    }
  } catch (e) {
    console.error(e);
  } finally {
    await refetch();
  }
};

const onEditingModalClose = async () => {
  selectedNav.value = undefined;
  refetch();
};

const handleDelete = (nav: Nav) => {
  Dialog.warning({
    title: "是否确认删除当前的导航？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        apiClient.delete(
          `/apis/core.halo.run/v1alpha1/navs/${nav.metadata.name}`
        );

        Toast.success("删除成功");
      } catch (e) {
        console.error(e);
      } finally {
        queryClient.invalidateQueries({ queryKey: ["navs"] });
      }
    },
  });
};

const handleDeleteInBatch = () => {
  Dialog.warning({
    title: "是否确认删除所选的导航？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        const promises = selectedNavs.value.map((nav) => {
          return apiClient.delete(`/apis/core.halo.run/v1alpha1/navs/${nav}`);
        });
        if (promises) {
          await Promise.all(promises);
        }

        selectedNavs.value.length = 0;
        checkedAll.value = false;

        Toast.success("删除成功");
      } catch (e) {
        console.error(e);
      } finally {
        queryClient.invalidateQueries({ queryKey: ["navs"] });
      }
    },
  });
};

const handleExportSelectedNavs = async () => {
  if (!navs.value?.length) {
    return;
  }
  const yamlString = navs.value
    .map((nav) => {
      if (selectedNavs.value.includes(nav.metadata.name)) {
        return yaml.stringify(nav);
      }
    })
    .filter((nav) => nav)
    .join("---\n");
  const blob = new Blob([yamlString], { type: "text/yaml" });
  const url = URL.createObjectURL(blob);
  const nav = document.createElement("a");
  nav.href = url;
  nav.download = "navs.yaml";
  nav.click();
};

const handleImportFromYaml = async () => {
  const res = useFileSystemAccess({
    dataType: "Text",
    types: [
      {
        description: "yaml",
        accept: {
          "text/yaml": [".yaml", ".yml"],
        },
      },
    ],
    excludeAcceptAllOption: true,
  });

  await res.open();

  try {
    if (!res.data.value) {
      return;
    }

    const parsed = yaml.parse(res.data.value);
    if (Array.isArray(parsed)) {
      const promises = parsed.map((nav) => {
        return apiClient.post("/apis/core.halo.run/v1alpha1/navs", nav);
      });
      if (promises) {
        await Promise.all(promises);
      }
    } else {
      await apiClient.post("/apis/core.halo.run/v1alpha1/navs", parsed);
    }
  } catch (e) {
    console.error(e);
  } finally {
    queryClient.invalidateQueries({ queryKey: ["navs"] });
  }
};

const handleCheckAllChange = (e: Event) => {
  const { checked } = e.target as HTMLInputElement;
  checkedAll.value = checked;
  if (checkedAll.value) {
    selectedNavs.value =
      navs.value?.map((nav) => {
        return nav.metadata.name;
      }) || [];
  } else {
    selectedNavs.value.length = 0;
  }
};

watch(selectedNavs, (newValue) => {
  checkedAll.value = newValue.length === navs.value?.length;
});

// groups
const { groups } = useNavGroupFetch();

function getGroup(groupName: string) {
  return groups.value?.find((group) => group.metadata.name === groupName);
}

async function handleMoveInBatch(group: NavGroup) {
  const navsToUpdate = selectedNavs.value
    ?.map((name) => {
      return navs.value?.find((nav) => nav.metadata.name === name);
    })
    .filter(Boolean) as Nav[];

  const requests = navsToUpdate.map((nav) => {
    return apiClient.put<Nav>(
      `/apis/core.halo.run/v1alpha1/navs/${nav?.metadata.name}`,
      {
        ...nav,
        spec: {
          ...nav.spec,
          groupName: group.metadata.name,
        },
      }
    );
  });

  if (requests) await Promise.all(requests);

  refetch();

  selectedNavs.value.length = 0;
  checkedAll.value = false;

  Toast.success("移动成功");
}

async function handleMove(nav: Nav, group: NavGroup) {
  await apiClient.put<Nav>(
    `/apis/core.halo.run/v1alpha1/navs/${nav.metadata.name}`,
    {
      ...nav,
      spec: {
        ...nav.spec,
        groupName: group.metadata.name,
      },
    }
  );

  Toast.success("移动成功");

  refetch();
}
</script>
<template>
  <NavEditingModal
    v-model:visible="editingModal"
    :nav="selectedNav"
    @close="onEditingModalClose"
  >
    <template #append-actions>
      <span @click="handleSelectPrevious">
        <IconArrowLeft />
      </span>
      <span @click="handleSelectNext">
        <IconArrowRight />
      </span>
    </template>
  </NavEditingModal>
  <VPageHeader title="导航">
    <template #actions>
      <VSpace v-permission="['plugin:navs:manage']">
        <VButton size="sm" type="default" @click="handleImportFromYaml">
          导入
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class="navs-p-4">
    <div class="navs-flex navs-flex-row navs-gap-2">
      <div class="navs-w-96">
        <GroupList />
      </div>
      <div class="navs-flex-1">
        <VCard :body-class="['!p-0']">
          <template #header>
            <div
              class="navs-block navs-w-full navs-bg-gray-50 navs-px-4 navs-py-3"
            >
              <div
                class="navs-relative navs-flex navs-flex-col navs-items-start sm:navs-flex-row sm:navs-items-center"
              >
                <div
                  class="navs-mr-4 navs-hidden navs-items-center sm:navs-flex"
                >
                  <input
                    v-model="checkedAll"
                    class="navs-h-4 navs-w-4 navs-rounded navs-border-gray-300 navs-text-indigo-600"
                    type="checkbox"
                    @change="handleCheckAllChange"
                  />
                </div>
                <div
                  class="navs-flex navs-w-full navs-flex-1 sm:navs-w-auto"
                >
                  <FormKit
                    v-if="!selectedNavs.length"
                    type="form"
                    @submit="onKeywordChange"
                  >
                    <FormKit
                      outer-class="!p-0"
                      placeholder="输入关键词搜索"
                      type="text"
                      name="keyword"
                      :model-value="keyword"
                    ></FormKit>
                  </FormKit>
                  <VSpace v-else>
                    <VButton type="danger" @click="handleDeleteInBatch">
                      删除
                    </VButton>
                    <VDropdown>
                      <VButton type="default">更多</VButton>
                      <template #popper>
                        <VDropdownItem @click="handleExportSelectedNavs">
                          导出
                        </VDropdownItem>
                        <VDropdownDivider />
                        <VDropdown placement="right" :triggers="['click']">
                          <VDropdownItem> 移动 </VDropdownItem>
                          <template #popper>
                            <template
                              v-for="group in groups"
                              :key="group.metadata.name"
                            >
                              <VDropdownItem
                                v-if="group.metadata.name !== groupQuery"
                                v-close-popper.all
                                @click="handleMoveInBatch(group)"
                              >
                                {{ group.spec.displayName }}
                              </VDropdownItem>
                            </template>
                          </template>
                        </VDropdown>
                      </template>
                    </VDropdown>
                  </VSpace>
                </div>
                <div
                  v-permission="['plugin:navs:manage']"
                  class="navs-mt-4 navs-flex sm:navs-mt-0"
                >
                  <VButton size="xs" @click="editingModal = true">
                    新建
                  </VButton>
                </div>
              </div>
            </div>
          </template>
          <VLoading v-if="isLoading" />
          <Transition v-else-if="!navs?.length" appear name="fade">
            <VEmpty message="你可以尝试刷新或者新建导航" title="当前没有导航">
              <template #actions>
                <VSpace>
                  <VButton @click="refetch"> 刷新</VButton>
                  <VButton
                    v-permission="['system:menus:manage']"
                    type="primary"
                    @click="editingModal = true"
                  >
                    <template #icon>
                      <IconAddCircle class="h-full w-full" />
                    </template>
                    新建
                  </VButton>
                </VSpace>
              </template>
            </VEmpty>
          </Transition>
          <Transition v-else appear name="fade">
            <Draggable
              v-model="draggableNavs"
              class="navs-box-border navs-h-full navs-w-full navs-divide-y navs-divide-gray-100"
              group="nav"
              handle=".drag-element"
              item-key="id"
              tag="ul"
              @end="drag = false"
              @start="drag = true"
              @change="onPriorityChange"
            >
              <template #item="{ element: nav }">
                <li>
                  <VEntity
                    :is-selected="selectedNavs.includes(nav.metadata.name)"
                    class="navs-group"
                  >
                    <template v-if="!keyword && groupQuery" #prepend>
                      <div
                        class="drag-element navs-absolute navs-inset-y-0 navs-left-0 navs-hidden navs-w-3.5 navs-cursor-move navs-items-center navs-bg-gray-100 navs-transition-all hover:navs-bg-gray-200 group-hover:navs-flex"
                      >
                        <IconList class="h-3.5 w-3.5" />
                      </div>
                    </template>

                    <template #checkbox>
                      <input
                        v-model="selectedNavs"
                        :value="nav.metadata.name"
                        class="navs-h-4 navs-w-4 navs-rounded navs-border-gray-300 navs-text-indigo-600"
                        name="post-checkbox"
                        type="checkbox"
                      />
                    </template>

                    <template #start>
                      <VEntityField>
                        <template #description>
                          <VAvatar
                            :key="nav.metadata.name"
                            :alt="nav.spec.displayName"
                            :src="nav.spec.logo"
                            size="md"
                          ></VAvatar>
                        </template>
                      </VEntityField>
                      <VEntityField :title="nav.spec.displayName">
                        <template #description>
                          <a
                            :href="nav.spec.url"
                            class="navs-truncate navs-text-xs navs-text-gray-500 hover:navs-text-gray-900"
                            target="_blank"
                          >
                            {{ nav.spec.url }}
                          </a>
                        </template>
                      </VEntityField>
                    </template>

                    <template #end>
                      <VEntityField
                        v-if="getGroup(nav.spec.groupName)"
                        :description="
                          getGroup(nav.spec.groupName)?.spec.displayName
                        "
                      />
                      <VEntityField v-if="nav.metadata.deletionTimestamp">
                        <template #description>
                          <VStatusDot
                            v-tooltip="`删除中`"
                            state="warning"
                            animate
                          />
                        </template>
                      </VEntityField>
                      <VEntityField
                        :description="
                          formatDatetime(nav.metadata.creationTimestamp)
                        "
                      />
                    </template>
                    <template #dropdownItems>
                      <VDropdownItem @click="handleOpenCreateModal(nav)">
                        编辑
                      </VDropdownItem>
                      <VDropdown placement="left" :triggers="['click']">
                        <VDropdownItem> 移动 </VDropdownItem>
                        <template #popper>
                          <template
                            v-for="group in groups"
                            :key="group.metadata.name"
                          >
                            <VDropdownItem
                              v-if="group.metadata.name !== groupQuery"
                              v-close-popper.all
                              @click="handleMove(nav, group)"
                            >
                              {{ group.spec.displayName }}
                            </VDropdownItem>
                          </template>
                        </template>
                      </VDropdown>
                      <VDropdownItem type="danger" @click="handleDelete(nav)">
                        删除
                      </VDropdownItem>
                    </template>
                  </VEntity>
                </li>
              </template>
            </Draggable>
          </Transition>

          <template #footer>
            <VPagination
              v-model:page="page"
              v-model:size="size"
              :total="total"
              :size-options="[20, 30, 50, 100]"
            />
          </template>
        </VCard>
      </div>
    </div>
  </div>
</template>
