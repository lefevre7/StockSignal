plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName = "gemma3_1b_model"
    dynamicDelivery {
        deliveryType = "on-demand"
    }
}
