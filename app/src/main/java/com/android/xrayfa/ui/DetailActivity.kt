package com.android.xrayfa.ui

import android.R.attr.progress
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
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
    companion object { const val TAG = "DetailActivity"}

    private var sourceX = 0
    private var sourceY = 0
    private var sourceW = 0
    private var sourceH = 0

    private lateinit var rootView: View
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }

        rootView = findViewById(android.R.id.content)

        initAnimationParams()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setupPredictiveBack()
        } else {
            onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    runExitAnimation0()
                }

            })
        }

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupPredictiveBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {

            // 1. 手势开始：设置锚点
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                rootView.pivotX = 0f
                rootView.pivotY = 0f
            }

            // 2. 手势进行中：根据进度更新 UI
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val progress = backEvent.progress // 0.0 到 1.0
                Log.d("XrayAnim", "Progress: $progress, W: $sourceW, H: $sourceH")

                if (sourceW == 0 || sourceH == 0) return

                val screenW = rootView.width.toFloat()
                val screenH = rootView.height.toFloat()

                // 计算最终目标的 Scale
                val targetScaleX = sourceW.toFloat() / screenW
                val targetScaleY = sourceH.toFloat() / screenH

                // 算法：当前状态 = 初始状态 + (目标状态 - 初始状态) * 进度
                // 这里为了手感，可以让进度最多只走到 50% 的动画效果，或者完全跟随
                // 建议：完全跟随手指的线性插值

                val currentScaleX = 1f + (targetScaleX - 1f) * progress
                val currentScaleY = 1f + (targetScaleY - 1f) * progress

                val currentTransX = sourceX.toFloat() * progress
                val currentTransY = sourceY.toFloat() * progress

                // 稍微增加一点透明度变化效果
                val currentAlpha = 1f - (progress * 0.5f) // 最多变到 0.5 透明度

                rootView.scaleX = currentScaleX
                rootView.scaleY = currentScaleY
                rootView.translationX = currentTransX
                rootView.translationY = currentTransY
                rootView.alpha = currentAlpha
            }

            // 3. 手势取消：回弹复原
            override fun handleOnBackCancelled() {
                rootView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }

            // 4. 手势确认：执行剩余的退出动画并 Finish
            override fun handleOnBackPressed() {
                // 此时 View 可能处于半缩放状态，直接从当前状态动画到最终状态
                runExitAnimation0()
            }
        })
    }

    @Composable
    override fun Content(isLandscape: Boolean) {
        val protocol = intent.getStringExtra(XrayViewmodel.EXTRA_PROTOCOL)
        val content = intent.getStringExtra(XrayViewmodel.EXTRA_LINK)
        val viewmodel =
            ViewModelProvider.create(this,detailViewmodelFactory)[DetailViewmodel::class.java]
        DetailContainer(
            protocol = protocol!!,
            content = content!!,
            detailViewmodel = viewmodel
        )
    }

    private fun initAnimationParams() {
        sourceX = intent.getIntExtra("ANIM_SOURCE_X", 0)
        sourceY = intent.getIntExtra("ANIM_SOURCE_Y", 0)
        sourceW = intent.getIntExtra("ANIM_SOURCE_W", 0)
        sourceH = intent.getIntExtra("ANIM_SOURCE_H", 0)
    }

    private fun runExitAnimation0() {
        val sourceX = intent.getIntExtra("ANIM_SOURCE_X", 0)
        val sourceY = intent.getIntExtra("ANIM_SOURCE_Y", 0)
        val sourceW = intent.getIntExtra("ANIM_SOURCE_W", 0)
        val sourceH = intent.getIntExtra("ANIM_SOURCE_H", 0)

        if (sourceW == 0 || sourceH == 0) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val cardColor = intent.getIntExtra("ANIM_BG_COLOR", Color.WHITE)
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val screenW = rootView.width.toFloat()
        val screenH = rootView.height.toFloat()

        val scaleX = sourceW.toFloat() / screenW
        val scaleY = sourceH.toFloat() / screenH


        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        rootView.setBackgroundColor(cardColor)

        rootView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val cornerRadius = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics
                )
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        rootView.clipToOutline = true

        val contentView = rootView.getChildAt(0)
        contentView?.animate()
            ?.alpha(0f)         // 让文字内容变透明
            ?.setDuration(100)  // 动作要快，100ms内文字消失，只留背景
            ?.start()

        rootView.pivotX = 0f
        rootView.pivotY = 0f

        rootView.animate()
            .setDuration(300) // 背景缩放慢一点，看清楚轨迹
            // .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f)) // 建议用这种 Material 风格的贝塞尔曲线，更有质感
            .setInterpolator(DecelerateInterpolator())
            .scaleX(scaleX)
            .scaleY(scaleY)
            .translationX(sourceX.toFloat())
            .translationY(sourceY.toFloat())
            // 注意：这里不要再让 rootView alpha 变 0 了，否则背景也没了
            // .alpha(0f)  <-- 去掉这行
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    private fun runExitAnimation() {
        val sourceX = intent.getIntExtra("ANIM_SOURCE_X", 0)
        val sourceY = intent.getIntExtra("ANIM_SOURCE_Y", 0)
        val sourceW = intent.getIntExtra("ANIM_SOURCE_W", 0)
        val sourceH = intent.getIntExtra("ANIM_SOURCE_H", 0)
        Log.d(TAG, "runExitAnimation: $sourceX $sourceY $sourceW $sourceH")
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