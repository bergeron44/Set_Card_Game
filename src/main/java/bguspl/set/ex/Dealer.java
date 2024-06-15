package bguspl.set.ex;

import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    public final BlockingQueue<Player> contendersToSet;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The players threads
     */
    private final ThreadLogger[] playersThreads;

    /**
     * Lock for the dealer
     */
    private final Object dealerLock;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.playersThreads = new ThreadLogger[players.length];
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        contendersToSet = new LinkedBlockingQueue<>();
        dealerLock = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        // start players threads
        for (int i = 0; i < players.length; i++) {
            playersThreads[i] = new ThreadLogger(players[i], "player " + i, env.logger);
            playersThreads[i].startWithLog();
        }

        // the game start
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }

        for (int i = players.length; i >= 0; i++) {
            players[i].terminate();
            players[i].join();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        updateTimerDisplay(true);
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        Thread.currentThread().interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if (contendersToSet.isEmpty()) {
            return;
        }
        // start check if there is a set for one of the players
        synchronized (dealerLock) {
            while (!contendersToSet.isEmpty()) {
                Player player = contendersToSet.remove();
                int[] playerCards = player.getCards();
                boolean isSet = env.util.testSet(playerCards);
                // Reaching this code isSet tells us whether the player has a legitimate set
                if (isSet) {
                        player.point();
                        updateTimerDisplay(true);
                        for (int card : playerCards) {
                            for (Player p : players) {
                                p.removeToken(table.cardToSlot[card]);
                            }
                            deck.remove(card);
                            table.removeCard(table.cardToSlot[card]);
                        }
                } else {
                    player.penalty();
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        Collections.shuffle(deck);
        int startingCards = table.countCards();
        synchronized (table) {
            for (Integer i = 0; i < 12; i++) {
                if (!deck.isEmpty()) {

                    if (table.slotToCard[i] == null) {
                        table.placeCard(deck.get(i), i);
                    }
                } else {
                    terminate();
                }
            }
        }

        if (table.countCards() > startingCards && env.config.hints)
            table.hints();

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            if (contendersToSet.isEmpty()) {
                synchronized (this) {
                    this.wait(100);
                }
            }
        } catch (InterruptedException e) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            this.reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(this.reshuffleTime - System.currentTimeMillis(), false);
        } else {
            if (this.reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
                env.ui.setCountdown(this.reshuffleTime - System.currentTimeMillis(), true);
            } else
                env.ui.setCountdown(this.reshuffleTime - System.currentTimeMillis(), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table) {
            for (Integer i = 0; i < 12; i++) {
                table.removeCard(i);
            }
            for (Player player : players) {
                player.removeTokens();
            }
            env.ui.removeTokens();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        Stack<Player> winners = new Stack<>();
        int points = 0;
        for (Player player : players) {
            if (player.score() == points) {
                winners.push(player);
            } else if (player.score() > points) {
                winners.clear();
                winners.push(player);
            }
        }
        int[] winnersID = new int[winners.size()];
        for (int i = 0; i < winnersID.length; i++) {
            winnersID[i] = winners.pop().id;
        }
        env.ui.announceWinner(winnersID);
        terminate();
    }
}
