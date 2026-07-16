import path from "path";
import { defineConfig } from "vite";
import { devtools } from "@tanstack/devtools-vite";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import viteReact from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { nitro } from "nitro/vite";

const config = defineConfig({
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  plugins: [
    devtools(),
    nitro(),
    tailwindcss(),
    tanstackStart(),
    // @vitejs/plugin-react v6's exported Options type omits `babel`, but the
    // plugin still accepts it at runtime (used here for the React Compiler).
    viteReact({
      babel: {
        plugins: [["babel-plugin-react-compiler", {}]],
      },
    } as unknown as Parameters<typeof viteReact>[0]),
  ],
});

export default config;
