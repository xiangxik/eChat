export interface RuntimeEnv {
  VITE_API_BASE_URL?: string;
  VITE_CHATBOT_ID?: string;
  VITE_CHATBOT_NAME?: string;
}

declare global {
  interface Window {
    __ECHAT_ENV__?: RuntimeEnv;
  }
}

export function readRuntimeEnv(name: keyof RuntimeEnv): string | undefined {
  const runtimeValue = window.__ECHAT_ENV__?.[name];
  if (typeof runtimeValue === 'string' && runtimeValue.length > 0) {
    return runtimeValue;
  }

  const buildValue = import.meta.env[name];
  return typeof buildValue === 'string' && buildValue.length > 0 ? buildValue : undefined;
}
