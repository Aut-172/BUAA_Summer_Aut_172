import React, { useEffect, useState } from 'react'
import { normalizeImageSrc } from '../utils/demoImages'

export function LightboxImage({ src, alt, className, buttonClassName }) {
    const [activeImage, setActiveImage] = useState(null)
    const imageSrc = normalizeImageSrc(src)

    useEffect(() => {
        if (!activeImage) {
            return undefined
        }

        function handleKeyDown(event) {
            if (event.key === 'Escape') {
                setActiveImage(null)
            }
        }

        window.addEventListener('keydown', handleKeyDown)
        return () => window.removeEventListener('keydown', handleKeyDown)
    }, [activeImage])

    if (!src) {
        return null
    }

    return (
        <>
            <button className={buttonClassName || 'lightbox-image-button'} type="button" onClick={() => setActiveImage(imageSrc)}>
                <img className={className} src={imageSrc} alt={alt} />
            </button>

            {activeImage ? (
                <div className="review-lightbox" role="dialog" aria-modal="true" onClick={() => setActiveImage(null)}>
                    <button className="review-lightbox-close" type="button" onClick={() => setActiveImage(null)}>
                        关闭
                    </button>
                    <img className="review-lightbox-image" src={activeImage} alt={alt} onClick={(event) => event.stopPropagation()} />
                </div>
            ) : null}
        </>
    )
}

export default function ReviewImageGallery({ images = [], alt = '评价图片', onRemove }) {
    if (!images.length) {
        return null
    }

    return (
        <>
            <div className="image-preview-grid">
                {images.map((src) => (
                    <div className="review-preview-item" key={src}>
                        <LightboxImage
                            src={src}
                            alt={alt}
                            className="review-preview"
                            buttonClassName="review-preview-button"
                        />
                        {onRemove ? (
                            <button className="btn ghost small" type="button" onClick={() => onRemove(src)}>
                                移除
                            </button>
                        ) : null}
                    </div>
                ))}
            </div>
        </>
    )
}
