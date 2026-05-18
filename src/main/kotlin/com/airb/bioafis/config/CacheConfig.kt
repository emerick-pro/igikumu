package com.airb.bioafis.config

import org.springframework.context.annotation.Configuration

@Configuration
class CacheConfig
// TemplateCache is a @Component with its own ConcurrentHashMap and ReentrantReadWriteLock.
// No Spring Cache abstraction is used — the cache is managed manually for full control
// over the biometric template lifecycle (warmup, per-tenant isolation, write-through).
