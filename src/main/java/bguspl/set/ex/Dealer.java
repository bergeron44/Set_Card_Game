package bguspl.set.ex;
import java.util.*;
import bguspl.set.Env;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        contendersToSet = new LinkedBlockingQueue<>();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        for (Player player : players) {
            new Thread(player, env.config.playerNames[player.id]).start();
        }
        System.out.println("players are running");

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
         for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
            players[i].join();
        }
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

            synchronized (this) {
                this.notifyAll();
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        int startingCards = table.countCards();
        for (Integer i = startingCards; i < 12; i++)
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
            while (contendersToSet.size() == 0 && reshuffleTime - System.currentTimeMillis() > 0) {
                synchronized (this) {
                    this.wait(100);
                }
                updateTimerDisplay(false);
            }
            // if (System.currentTimeMillis() > timeout) {
            // this.warning = true;
            // System.out.println("start warning time");
            // updateTimerDisplay(false);
            // timeout = System.currentTimeMillis() + env.config.turnTimeoutWarningMillis;
            // synchronized (this) {
            // this.wait(timeout - System.currentTimeMillis());
            // }
            // }
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
        for (Integer i = 0; i < 12; i++) {
            deck.add(i);
            table.removeCard(i);
        }

        for (Integer i = 0; i < 12; i++) {
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
