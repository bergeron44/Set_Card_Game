package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public final Queue<Player> contendersToSet;
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

    private boolean warning;
    private long timeout;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.warning = false;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        contendersToSet = new PriorityQueue<>();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
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
        // TODO implement
        for (Player player : players) {
            player.terminate();
        }
        terminate = true;
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
        // TODO implement
        if (contendersToSet.isEmpty()) {
            return;
        }

        while (!contendersToSet.isEmpty()) {
            Player player = contendersToSet.remove();
            int[] playerCards = player.getCards();
            boolean isSet = true;
            for (int i = 0; i < 3; i++) {
                if (playerCards[i] == -1)
                    isSet = false;
            }
            isSet = env.util.testSet(playerCards);
            player.point();

            // Reaching this code isSet tells us whether the player has a legitimate set
            if (isSet) {
                synchronized (table) {
                    updateTimerDisplay(true);
                    for (int card : playerCards) {
                        table.removeCard(card);
                    }
                    for (Player p : players) {
                        for (int card : playerCards) {
                            p.removeToken(table.cardToSlot[card]);
                        }
                    }
                }
            } else {
                player.penalty();
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        int startingCards = table.countCards();
        for (Integer i = startingCards; i <= 12; i++)
            if (!deck.isEmpty())
                synchronized (table) {
                    table.placeCard(deck.get(0), i);
                    deck.remove(0);
                }

        if (table.countCards() > startingCards && env.config.hints)
            table.hints();

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            this.timeout = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            Thread.currentThread().wait(timeout - System.currentTimeMillis());
            if (System.currentTimeMillis() > timeout) {
                this.warning = true;
                updateTimerDisplay(false);
                timeout = System.currentTimeMillis() + env.config.turnTimeoutWarningMillis;
                Thread.currentThread().wait(timeout - System.currentTimeMillis());
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private synchronized void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            env.ui.setCountdown(env.config.turnTimeoutWarningMillis, false);
            this.reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutWarningMillis
                    + env.config.turnTimeoutMillis;
        } else if (warning) {
            this.warning=false;
            env.ui.setCountdown(env.config.turnTimeoutWarningMillis, true);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (Integer i = 0; i <= 12; i++) {
            deck.add(i);
            table.removeCard(i);
        }

        for (Integer i = 0; i <= 12; i++) {
            if (!deck.isEmpty()) {
                table.placeCard(0, i);
                deck.remove(0);
            }
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

    /**
     * Checks if the player have a set and reward/panish him accordingly
     */
    public void isSet(int[] slots, int id) {
        if (env.util.testSet(slots)) {
            synchronized (table) {
                updateTimerDisplay(false);
                for (int card : slots) {
                    table.removeCard(card);
                }
                for (Player player : players) {
                    for (int slot : slots) {
                        player.removeToken(slot);
                    }
                    if (player.id == id) {
                        player.point();
                    }
                }
            }
        } else {
            for (Player player : players) {
                if (player.id == id) {
                    player.penalty();
                    break;
                }
            }
        }
    }
}
