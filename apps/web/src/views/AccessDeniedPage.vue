<template>
  <main class="not-found forbidden-page">
    <p class="eyebrow">
      访问控制
    </p>
    <h1>当前账号无权访问</h1>
    <p class="description">
      此页面要求的角色或权限不在当前账号范围内。菜单显隐仅用于导航，实际访问仍由服务端授权。
    </p>
    <RouterLink to="/">
      返回运行状态页
    </RouterLink>
  </main>
</template>
