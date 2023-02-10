package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class FengYunBianHuan extends Card {
    private static final Logger log = Logger.getLogger(FengYunBianHuan.class);

    public FengYunBianHuan(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public FengYunBianHuan(int id, Card card) {
        super(id, card);
    }

    /**
     * 仅用于“作为风云变幻使用”
     */
    FengYunBianHuan(Card originCard) {
        super(originCard);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Feng_Yun_Bian_Huan;
    }

    @Override
    public boolean canUse(final Game g, final Player r, Object... args) {
        if (r == g.getJinBiPlayer()) {
            log.error("你被禁闭了，不能出牌");
            return false;
        }
        if (g.getQiangLingTypes().contains(getType())) {
            log.error("风云变幻被禁止使用了");
            return false;
        }
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("风云变幻的使用时机不对");
            return false;
        }
        return true;
    }

    @Override
    public void execute(Game g, Player r, Object... args) {
        var fsm = (MainPhaseIdle) g.getFsm();
        r.deleteCard(this.id);
        Deque<Player> players = new ArrayDeque<>();
        for (Player player : r.getGame().getPlayers()) {
            if (player.isAlive())
                players.add(player);
        }
        Map<Integer, Card> drawCards = new HashMap<>();
        for (Card c : r.getGame().getDeck().draw(players.size())) {
            drawCards.put(c.getId(), c);
        }
        while (players.size() > drawCards.size()) {
            players.removeLast(); // 兼容牌库抽完的情况
        }
        log.info(r + "使用了" + this + "，翻开了" + Arrays.toString(drawCards.values().toArray(new Card[0])));
        for (Player player : r.getGame().getPlayers()) {
            if (player instanceof HumanPlayer p) {
                var builder = Fengsheng.use_feng_yun_bian_huan_toc.newBuilder();
                builder.setCard(this.toPbCard()).setPlayerId(p.getAlternativeLocation(r.location()));
                for (Card c : drawCards.values()) {
                    builder.addShowCards(c.toPbCard());
                }
                p.send(builder.build());
            }
        }
        Fsm resolveFunc = () -> new ResolveResult(new executeFengYunBianHuan(this, drawCards, players, fsm), true);
        g.resolve(new OnUseCard(r, r, null, this, Common.card_type.Feng_Yun_Bian_Huan, r, resolveFunc));
    }

    private record executeFengYunBianHuan(FengYunBianHuan card, Map<Integer, Card> drawCards, Queue<Player> players,
                                          MainPhaseIdle mainPhaseIdle) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeFengYunBianHuan.class);

        @Override
        public ResolveResult resolve() {
            Player r = players.peek();
            if (r == null) {
                mainPhaseIdle.player().getGame().getDeck().discard(card);
                return new ResolveResult(mainPhaseIdle, true);
            }
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.wait_for_feng_yun_bian_huan_choose_card_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(15);
                    if (p == r) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        p.setTimeout(GameExecutor.post(r.getGame(), () -> {
                            if (p.checkSeq(seq2)) {
                                p.incrSeq();
                                autoChooseCard();
                            }
                        }, p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(r.getGame(), this::autoChooseCard, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (!(message instanceof Fengsheng.feng_yun_bian_huan_choose_card_tos msg)) {
                log.error("现在正在结算风云变幻");
                return null;
            }
            Card chooseCard = drawCards.get(msg.getCardId());
            if (chooseCard == null) {
                log.error("没有这张牌");
                return null;
            }
            assert !players.isEmpty();
            if (player != players.peek()) {
                log.error("还没轮到你选牌");
                return null;
            }
            if (msg.getAsMessageCard()) {
                boolean containsSame = false;
                for (Card c : player.getMessageCards().values()) {
                    if (c.hasSameColor(chooseCard)) {
                        containsSame = true;
                        break;
                    }
                }
                if (containsSame) {
                    log.error("已有相同颜色情报，不能作为情报牌");
                    return null;
                }
            }
            player.incrSeq();
            players.poll();
            drawCards.remove(chooseCard.getId());
            if (msg.getAsMessageCard()) {
                player.addMessageCard(chooseCard);
            } else {
                player.addCard(chooseCard);
            }
            return new ResolveResult(this, true);
        }

        private void autoChooseCard() {
            assert !drawCards.isEmpty();
            assert !players.isEmpty();
            Card chooseCard = null;
            for (Card c : drawCards.values()) {
                chooseCard = c;
                break;
            }
            Player r = players.peek();
            var builder = Fengsheng.feng_yun_bian_huan_choose_card_tos.newBuilder();
            builder.setCardId(chooseCard.getId());
            builder.setAsMessageCard(false);
            if (r instanceof HumanPlayer humanPlayer)
                builder.setSeq(humanPlayer.getSeq());
            r.getGame().tryContinueResolveProtocol(r, builder.build());
        }
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "风云变幻";
    }

    public static boolean ai(MainPhaseIdle e, Card card) {
        Player player = e.player();
        if (player.getGame().getQiangLingTypes().contains(Common.card_type.Feng_Yun_Bian_Huan))
            return false;
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player), 2, TimeUnit.SECONDS);
        return true;
    }
}
