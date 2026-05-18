package com.airb.bioafis.security

import com.airb.bioafis.domain.model.Tenant

object TenantContext {
    private val holder = ThreadLocal<Tenant?>()

    fun set(tenant: Tenant) {
        holder.set(tenant)
    }

    fun get(): Tenant? = holder.get()

    fun clear() {
        holder.remove()
    }

    fun require(): Tenant = get() ?: error("No tenant in context")
}
