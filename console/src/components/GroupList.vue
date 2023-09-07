<script lang="ts" setup>
import {
  VButton,
  VCard,
  VEntity,
  IconList,
  VEntityField,
  VStatusDot,
  Dialog,
  VLoading,
  VDropdownItem,
  Toast,
} from "@halo-dev/components";
import GroupEditingModal from "./GroupEditingModal.vue";
import type { NavGroup, NavList } from "@/types";
import { inject, ref, watch, type Ref } from "vue";
import Draggable from "vuedraggable";
import apiClient from "@/utils/api-client";
import { useNavGroupFetch } from "@/composables/use-nav";
import cloneDeep from "lodash.clonedeep";

const groupQuery = inject<Ref<string>>("groupQuery", ref(""));

const groupEditingModal = ref(false);
const selectedGroup = ref<NavGroup>();

const { groups, isLoading, refetch } = useNavGroupFetch();
const draggableGroups = ref<NavGroup[]>();

watch(
  () => groups.value,
  () => {
    draggableGroups.value = cloneDeep(groups.value);
  },
  {
    immediate: true,
  }
);

const handleOpenEditingModal = (group?: NavGroup) => {
  selectedGroup.value = group;
  groupEditingModal.value = true;
};

function onOpenCreateByParentModal(group: NavGroup) {
  selectedGroup.value = group;
  groupEditingModal.value = true;
}

const onPriorityChange = async () => {
  try {
    const promises = draggableGroups.value?.map((group: NavGroup, index) => {
      if (group.spec) {
        group.spec.priority = index;
      }
      return apiClient.put(
        `/apis/core.halo.run/v1alpha1/navgroups/${group.metadata.name}`,
        group
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

const handleDelete = async (group: NavGroup) => {
  Dialog.warning({
    title: "确定要删除该分组吗？",
    description: "将同时删除该分组下的所有链接，该操作不可恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        await apiClient.delete(
          `/apis/core.halo.run/v1alpha1/navgroups/${group.metadata.name}`
        );

        const { data } = await apiClient.get<NavList>(
          `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginNavs/navs`,
          {
            params: {
              page: 0,
              size: 0,
              groupName: group.metadata.name,
            },
          }
        );

        const deleteNavPromises = data.items.map((nav) =>
          apiClient.delete(
            `/apis/core.halo.run/v1alpha1/navs/${nav.metadata.name}`
          )
        );

        if (deleteNavPromises) {
          await Promise.all(deleteNavPromises);
        }

        groupQuery.value = "";

        Toast.success("删除成功");
      } catch (e) {
        console.error("Failed to delete nav group", e);
      } finally {
        await refetch();
      }
    },
  });
};

function onEditingModalClose() {
  selectedGroup.value = undefined;
  refetch();
}
</script>
<template>
  <GroupEditingModal
    v-model:visible="groupEditingModal"
    :group="selectedGroup"
    @close="onEditingModalClose"
  />
  <VCard :body-class="['!p-0']" title="导航分组">
    <VLoading v-if="isLoading" />
    <Transition v-else appear name="fade">
      <Draggable
        v-model="draggableGroups"
        class="navs-box-border navs-h-full navs-w-full navs-divide-y navs-divide-gray-100"
        group="group"
        handle=".drag-element"
        item-key="metadata.name"
        tag="ul"
        @change="onPriorityChange"
      >
        <template #header>
          <li @click="groupQuery = ''">
            <VEntity class="navs-group" :is-selected="!groupQuery">
              <template #start>
                <VEntityField title="全部"> </VEntityField>
              </template>
            </VEntity>
          </li>
        </template>
        <template #item="{ element: group }">
          <li @click="groupQuery = group.metadata.name">
            <VEntity
              :is-selected="groupQuery === group.metadata.name"
              class="navs-group"
            >
              <template #prepend>
                <div
                  class="drag-element navs-absolute navs-inset-y-0 navs-left-0 navs-hidden navs-w-3.5 navs-cursor-move navs-items-center navs-bg-gray-100 navs-transition-all hover:navs-bg-gray-200 group-hover:navs-flex"
                >
                  <IconList class="h-3.5 w-3.5" />
                </div>
              </template>

              <template #start>
                <VEntityField :title="group.spec?.displayName"></VEntityField>
              </template>

              <template #end>
                <VEntityField v-if="group.metadata.deletionTimestamp">
                  <template #description>
                    <VStatusDot v-tooltip="`删除中`" state="warning" animate />
                  </template>
                </VEntityField>
              </template>

              <template #dropdownItems>
                <VDropdownItem @click="handleOpenEditingModal(group)">
                  修改
                </VDropdownItem>
                <VDropdownItem @click="onOpenCreateByParentModal(group)">
                  添加子菜单
                </VDropdownItem>
                <VDropdownItem type="danger" @click="handleDelete(group)">
                  删除
                </VDropdownItem>
              </template>
            </VEntity>
          </li>
        </template>
      </Draggable>
    </Transition>

    <template v-if="!isLoading" #footer>
      <Transition appear name="fade">
        <VButton
          v-permission="['plugin:navs:manage']"
          block
          type="secondary"
          @click="handleOpenEditingModal(undefined)"
        >
          新建
        </VButton>
      </Transition>
    </template>
  </VCard>
</template>
