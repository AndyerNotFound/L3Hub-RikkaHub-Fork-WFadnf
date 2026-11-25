import type { ComponentPropsWithRef } from "react";

import { cn } from "~/lib/utils";

// 应用图标：直接使用 public/l3hub-icon.png 整张原稿，不做裁切或重绘
export default function Logo({
  className,
  alt = "L3Hub",
  ...props
}: ComponentPropsWithRef<"img">) {
  return (
    <img
      {...props}
      src="/l3hub-icon.png"
      alt={alt}
      width={64}
      height={64}
      draggable={false}
      className={cn("size-16 shrink-0 object-contain", className)}
    />
  );
}
