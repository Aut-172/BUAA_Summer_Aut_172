function svgImage(label, background, accent) {
    const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="480" height="320" viewBox="0 0 480 320">
            <rect width="480" height="320" rx="28" fill="${background}"/>
            <circle cx="396" cy="58" r="54" fill="${accent}" opacity="0.22"/>
            <circle cx="74" cy="258" r="78" fill="#ffffff" opacity="0.16"/>
            <path d="M112 218c44-58 77-86 103-86 21 0 35 18 52 37 13 15 27 29 46 29 18 0 38-12 61-38v82H112z" fill="#ffffff" opacity="0.20"/>
            <rect x="120" y="88" width="240" height="58" rx="14" fill="#ffffff" opacity="0.20"/>
            <text x="240" y="126" text-anchor="middle" font-family="Arial, sans-serif" font-size="28" font-weight="700" fill="#ffffff">${label}</text>
        </svg>
    `
    return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

export const FALLBACK_STORE_IMAGE = svgImage('Store', '#2f6f73', '#f2b84b')
export const FALLBACK_PRODUCT_IMAGE = svgImage('Product', '#795548', '#55b9a8')
export const FALLBACK_PROFILE_IMAGE = svgImage('User', '#536d8e', '#f2b84b')

const OSS_PUBLIC_BASE_URL = (
    import.meta.env.VITE_OSS_PUBLIC_BASE_URL ||
    '/oss'
).replace(/\/+$/, '')

export function normalizeImageSrc(value, fallback = FALLBACK_PRODUCT_IMAGE) {
    const raw = typeof value === 'string' ? value.trim().replace(/^['"]|['"]$/g, '') : ''
    if (!raw) {
        return fallback
    }

    if (raw.startsWith('//')) {
        return `https:${raw}`
    }

    if (/^(https?:|data:image\/|blob:|\/)/i.test(raw)) {
        return raw
    }

    if (/^[a-z0-9.-]+\.[a-z]{2,}(\/|$)/i.test(raw)) {
        return `https://${raw}`
    }

    if (/^(life-assistant|demo)\//i.test(raw)) {
        return `${OSS_PUBLIC_BASE_URL}/${raw.replace(/^\/+/, '')}`
    }

    return `/${raw.replace(/^\/+/, '')}`
}
