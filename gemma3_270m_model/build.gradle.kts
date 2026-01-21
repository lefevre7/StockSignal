plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName = "gemma3_270m_model"
    dynamicDelivery {
        deliveryType = "on-demand"
    }
}
