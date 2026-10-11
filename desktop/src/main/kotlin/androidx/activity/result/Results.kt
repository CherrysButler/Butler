package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContracts

class PickVisualMediaRequest(val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo)

abstract class ActivityResultLauncher<I> {
    abstract fun launch(input: I)
}
