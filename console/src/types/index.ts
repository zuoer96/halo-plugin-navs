export interface Metadata {
  name: string;
  generateName?: string;
  labels?: {
    [key: string]: string;
  } | null;
  annotations?: {
    [key: string]: string;
  } | null;
  version?: number | null;
  creationTimestamp?: string | null;
  deletionTimestamp?: string | null;
}

export interface NavGroupSpec {
  displayName: string;
  priority?: number;
  // @deprecated
  navs: string[];
}

export interface NavSpec {
  url: string;
  displayName: string;
  logo?: string;
  description?: string;
  priority?: number;
  groupName?: string;
}

// 与自定义模型 对应
export interface Nav {
  spec: NavSpec;
  apiVersion: string;
  kind: string;
  metadata: Metadata;
}

export interface NavGroup {
  spec: NavGroupSpec;
  apiVersion: string;
  kind: string; // 自定义模型中 @GVK 注解中的 kind
  metadata: Metadata;
}

export interface NavList {
  page: number;
  size: number;
  total: number;
  items: Array<Nav>;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  totalPages: number;
}

export interface NavGroupList {
  page: number;
  size: number;
  total: number;
  items: Array<NavGroup>;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  totalPages: number;
}
