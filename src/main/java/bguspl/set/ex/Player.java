package bguspl.set.ex;

import java.util.Stack;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {
    /**
     * Player Choices
     */
    private final Stack<Integer> cards;

    /**
     * The dealer object.
     */
    private final Dealer dealer;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The penelty time of the player
     */
    private long peneltyTime;

    /**
     * Lock for removeToken() and getCards()
     */
    private final Object lockObject;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.dealer = dealer;
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.peneltyTime = 0;
        this.cards = new Stack<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();
        while (!terminate) {
            if (cards.size() >= 3) {
                dealer.contendersToSet.add(this);
                try {
                    while (cards.size() >= 3) {
                        if (terminate)
                            break;
                        synchronized (this) {
                            wait();
                            while (peneltyTime - System.currentTimeMillis() >= 0) {
                                if (terminate)
                                    break;
                                env.ui.setFreeze(id, peneltyTime - System.currentTimeMillis());
                                wait(100);
                            }
                            env.ui.setFreeze(id, -1);
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                keyPressed((int) (Math.random() * table.countCards()));
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        if (!human) {
            aiThread.interrupt();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized (this) {
            synchronized (lockObject) {
                if (terminate || cards.size() >= 3 || this.peneltyTime - System.currentTimeMillis() >= 0||cards.size() >= 3) {
                    return;
                }
                if (!removeToken(slot)) {
                    cards.push(table.slotToCard[slot]);
                    table.placeToken(id, slot);
                }
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public synchronized  void point() {
        // TODO implement
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        score++;
        env.ui.setScore(id, score);
        peneltyTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
        int ignored = table.countCards(); // this part is just for demonstration in
        // the unit tests
        // env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized  void penalty() {
        // TODO implement
        cards.clear();
        peneltyTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
    }

    public int score() {
        return score;
    }

    /**
     * Remove the token from cards
     */
    public boolean removeToken(int slot) {
        synchronized (this) {
            for (Integer card : cards) {
                if (card == table.slotToCard[slot]) {
                    cards.remove(card);
                    table.removeToken(id, slot);
                    return true;
                }
            }
            return false;
        }
    }

    public boolean removeTokens() {

        try {
            synchronized (this) {
                cards.clear();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @return 3 cells length array that contain the cards in the stack
     */
    public int[] getCards() {
        int[] cardsArray = new int[3];
        for (int i = 0; i < cardsArray.length; i++) {
            cardsArray[i] = -1;
        }
        synchronized (lockObject) {
            int i = 0;
            for (i = 0; i < cardsArray.length; i++) {
                cardsArray[i] = cards.get(id);
                i++;
            }
        }
        return cardsArray;
    }

}
