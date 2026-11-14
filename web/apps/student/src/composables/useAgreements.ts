import { ref } from 'vue'
import type { AgreementDetail, AgreementRef, StudentAgreementType } from '@yunshu/api'
import { accountApi } from '@/api'

export type AgreementState = {
  versionNo: number
  content: string
  publishedAt: string | null
  /** 后台尚未发布该类协议时为 false：仍允许勾选同意，但弹窗无正文可展示 */
  published: boolean
}

const TYPES: StudentAgreementType[] = ['user_agreement', 'privacy_policy']

const empty = (): AgreementState => ({ versionNo: 0, content: '', publishedAt: null, published: false })

/**
 * 登录与注册需要提交两类协议的最新版本（S-ACC-03、04）。
 * 版本号由 S-HLP-01 读取；协议尚未发布时按 0 提交，服务端在未发布时不校验版本。
 */
export function useAgreements() {
  const states = ref<Record<StudentAgreementType, AgreementState>>({
    user_agreement: empty(),
    privacy_policy: empty(),
  })
  const loading = ref(false)
  const loaded = ref(false)

  async function load() {
    loading.value = true
    await Promise.all(
      TYPES.map(async (type) => {
        try {
          const detail: AgreementDetail = await accountApi.agreement(type)
          states.value[type] = {
            versionNo: detail.version_no,
            content: detail.content,
            publishedAt: detail.published_at,
            published: true,
          }
        } catch {
          states.value[type] = empty()
        }
      }),
    )
    loading.value = false
    loaded.value = true
  }

  /** 提交时用：版本号以最新读到的为准，未加载过则先加载 */
  async function buildRefs(): Promise<AgreementRef[]> {
    if (!loaded.value) await load()
    return TYPES.map((type) => ({ agreement_type: type, version_no: states.value[type].versionNo }))
  }

  return { states, loading, loaded, load, buildRefs }
}

export const AGREEMENT_LABEL: Record<StudentAgreementType, string> = {
  user_agreement: '用户协议',
  privacy_policy: '隐私政策',
}
