package ds.tetris.game

import ds.tetris.game.Direction.*
import ds.tetris.game.figures.*
import ds.tetris.game.job.KeyCoroutine
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.channels.ActorJob
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.selects.select
import kotlin.coroutines.experimental.CoroutineContext

private const val BASE_DELAY = 800L

private val figures = arrayOf(
    IFigure::class.java,
    IFigure::class.java,
    LFigure::class.java,
    LFlippedFigure::class.java,
    SFigure::class.java,
    SFlippedFigure::class.java,
    SquareFigure::class.java,
    TFigure::class.java
)

class Game(
    private val view: GameView,
    private val uiCoroutineContext: CoroutineContext
) {

    private val board: Board = Board(view, randomFigure())
    private val score: Score = Score {
        view.score = score
        view.level = level
    }
    private lateinit var gameActor: ActorJob<Unit>

    private var isStarted: Boolean = false
    var isPaused: Boolean = false   // todo

    private var stopper: Job = Job()

    private val uiContextProvider = {
        uiCoroutineContext + stopper
    }
    private var downKeyCoroutine: KeyCoroutine = KeyCoroutine(uiContextProvider) {
        score.awardSpeedUp()
        gameActor.offer(Unit)
    }
    private var leftKeyCoroutine: KeyCoroutine = KeyCoroutine(uiContextProvider, 100) {
        board.moveFigure(LEFT.movement)
    }
    private var rightKeyCoroutine: KeyCoroutine = KeyCoroutine(uiContextProvider, 100) {
        board.moveFigure(RIGHT.movement)
    }

    fun start() {
        if (isStarted) error("Can't start twice")
        isStarted = true

        view.clearArea()

        score.awardStart()

        gameActor = provideActor()
        gameActor.offer(Unit)
    }

    fun stop() {
        stopper.cancel()
    }

    fun pause() {
        isPaused = !isPaused
        if (!isPaused)
            gameActor.offer(Unit)
    }

    private fun provideActor(): ActorJob<Unit> = actor(uiContextProvider()) {
        log("actor started")
        while (isActive) {
            var falling = true
            board.drawFigure()
            while (falling) {
                falling = select {
                    onReceive {
                        board.moveFigure(DOWN.movement)
                    }
                    onTimeout(calculateDelay()) {
                        board.moveFigure(DOWN.movement)
                    }
                }
            }
            if (gameOver()) {
                isStarted = false
                coroutineContext.cancel()
            } else {
                board.fixFigure()

                val lines = board.getFilledLinesIndices()
                if (lines.isNotEmpty()) {
                    board.wipeLines(lines)
                    view.wipeLines(lines)
                    score.awardLinesWipe(lines.size)
                }

                board.currentFigure = randomFigure()
            }
        }
        view.gameOver()
        log("actor stopped")
    }

    private fun calculateDelay() = if (!isPaused) {
        (BASE_DELAY - score.level * 50).coerceAtLeast(1)
    } else {
        Long.MAX_VALUE
    }


    private fun gameOver(): Boolean = board.currentFigure.position.y <= 0

    private fun randomFigure(): Figure {
        val cls = figures.random
        val figure = cls.newInstance()
        figure.position = Point((AREA_WIDTH - figure.matrix.width) / 2, 0)
        //println(figure)
        return figure
    }

    fun onLeftPressed() = leftKeyCoroutine.start()
    fun onRightPressed() = rightKeyCoroutine.start()
    fun onUpPressed() = board.rotateFigure()
    fun onDownPressed() = downKeyCoroutine.start()
    fun onDownReleased() = downKeyCoroutine.stop()
    fun onLeftReleased() = leftKeyCoroutine.stop()
    fun onRightReleased() = rightKeyCoroutine.stop()

}