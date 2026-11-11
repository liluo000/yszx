export interface MenuItem {
  path: string
  title: string
  /** 权限码，与后端 AdminPermission 一致；登录后按当前账号的权限显示菜单 */
  permission: string
  component: () => Promise<unknown>
}

export interface MenuGroup {
  /** 分组名称，为空时不显示分组标题 */
  title: string
  items: MenuItem[]
}

/** 后台菜单，分组与原型一致 */
export const menuGroups: MenuGroup[] = [
  {
    title: '',
    items: [
      {
        path: '/workbench',
        title: '工作台',
        permission: 'workbench',
        component: () => import('@/pages/workbench/WorkbenchPage.vue'),
      },
    ],
  },
  {
    title: '内容管理',
    items: [
      {
        path: '/subjects',
        title: '学科管理',
        permission: 'subject',
        component: () => import('@/pages/content/SubjectPage.vue'),
      },
      {
        path: '/courses',
        title: '课程库',
        permission: 'course',
        component: () => import('@/pages/content/CoursePage.vue'),
      },
      {
        path: '/routes',
        title: '学习路线',
        permission: 'route',
        component: () => import('@/pages/content/RoutePage.vue'),
      },
      {
        path: '/practice-questions',
        title: '刷题题库',
        permission: 'practice_question',
        component: () => import('@/pages/question/PracticeQuestionPage.vue'),
      },
      {
        path: '/interview-questions',
        title: '面试题库',
        permission: 'interview_question',
        component: () => import('@/pages/question/InterviewQuestionPage.vue'),
      },
      {
        path: '/feedbacks',
        title: '纠错与反馈',
        permission: 'feedback',
        component: () => import('@/pages/feedback/FeedbackPage.vue'),
      },
    ],
  },
  {
    title: '学员与数据',
    items: [
      {
        path: '/students',
        title: '学员管理',
        permission: 'student',
        component: () => import('@/pages/studentops/StudentPage.vue'),
      },
      {
        path: '/dashboard',
        title: '数据看板',
        permission: 'dashboard',
        component: () => import('@/pages/analytics/DashboardPage.vue'),
      },
    ],
  },
  {
    title: '系统管理',
    items: [
      {
        path: '/sys-users',
        title: '系统用户',
        permission: 'sys_user',
        component: () => import('@/pages/iam/SysUserPage.vue'),
      },
      {
        path: '/roles',
        title: '角色管理',
        permission: 'role',
        component: () => import('@/pages/iam/RolePage.vue'),
      },
      {
        path: '/operation-logs',
        title: '操作日志',
        permission: 'operation_log',
        component: () => import('@/pages/audit/OperationLogPage.vue'),
      },
      {
        path: '/configs',
        title: '系统配置',
        permission: 'config',
        component: () => import('@/pages/sysconfig/ConfigPage.vue'),
      },
    ],
  },
]

export const menuItems: MenuItem[] = menuGroups.flatMap((group) => group.items)
