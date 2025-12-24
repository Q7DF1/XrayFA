package com.android.xrayfa.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.DetailContainer
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.DetailViewmodelFactory
import com.android.xrayfa.viewmodel.XrayViewmodel
import javax.inject.Inject

class DetailActivity
@Inject constructor(
    val detailViewmodelFactory: DetailViewmodelFactory
): XrayBaseActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
    }
    @Composable
    override fun Content() {
        val protocol = intent.getStringExtra(XrayViewmodel.EXTRA_PROTOCOL)
        val content = intent.getStringExtra(XrayViewmodel.EXTRA_LINK)
        val viewmodel =
            ViewModelProvider.create(this,detailViewmodelFactory)[DetailViewmodel::class.java]
        BackHandler{
            runExitAnimation()
        }
        DetailContainer(
            protocol = protocol!!,
            content = content!!,
            detailViewmodel = viewmodel
        )
    }


    private fun runExitAnimation() {
        val sourceX = intent.getIntExtra("ANIM_SOURCE_X", 0)
        val sourceY = intent.getIntExtra("ANIM_SOURCE_Y", 0)
        val sourceW = intent.getIntExtra("ANIM_SOURCE_W", 0)
        val sourceH = intent.getIntExtra("ANIM_SOURCE_H", 0)

        if (sourceW == 0 || sourceH == 0) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val rootView = findViewById<View>(android.R.id.content)
        val screenW = rootView.width.toFloat()
        val screenH = rootView.height.toFloat()

        val scaleX = sourceW.toFloat() / screenW
        val scaleY = sourceH.toFloat() / screenH

        rootView.pivotX = 0f
        rootView.pivotY = 0f

        rootView.animate()
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withLayer()
            .scaleX(scaleX)
            .scaleY(scaleY)
            .translationX(sourceX.toFloat())
            .translationY(sourceY.toFloat())
            .alpha(0f)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

}