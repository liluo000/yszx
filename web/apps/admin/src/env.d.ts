/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_STUDENT_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
