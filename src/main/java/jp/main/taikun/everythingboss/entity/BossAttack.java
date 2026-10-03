package jp.main.taikun.everythingboss.entity;

public enum BossAttack {
    NONE(0),
    /** 登場演出。枠が組み上がりアイテムが膨らむ。この間は無敵 */
    SPAWN(100, true),
    /** HP 50% での変身演出。この間は無敵で、終わり際に衝撃波 */
    ENRAGE(60, true),
    /** 近接: 振りかぶってから全周を薙ぎ払う */
    SWING(28),
    /** 近接: アイテムの先端を向けて突進 */
    DASH(46),
    /** 遠距離: 溜めてからアイテムを引き伸ばしたビームを照射。照射中はゆっくり追尾する */
    BEAM(100),
    /** 遠距離: アイテムの複製を扇状に連射 */
    BARRAGE(50),
    /** 遠距離 (発狂時): 対象の頭上からアイテムの雨 */
    RAIN(60),
    /** 近接 (発狂時): 浮き上がって真下に叩きつけ、衝撃波 */
    SLAM(80),

    // ---- 複合技
    /** 上空に昇り、ビームで狙い続けながら対象の頭上を突き抜ける */
    BEAM_DIVE(95),
    /** 斜め下にビームを撃ったまま一回転して地面に輪を描き、同時に全方位へ弾を撃つ */
    SPIN_BEAM(110),
    /** 突進 3 連 → 薙ぎ払い */
    DASH_COMBO(96),
    /** 真下へ予告ビームで着地点を示してから叩きつけ、着地でアイテムを放射状に 2 波撃つ */
    SLAM_BURST(90);

    /** 攻撃の最大長 (tick)。SLAM は着地で早めに終わる */
    public final int duration;
    /** 演出中で攻撃が効かない */
    public final boolean invulnerable;

    BossAttack(int duration) {
        this(duration, false);
    }

    BossAttack(int duration, boolean invulnerable) {
        this.duration = duration;
        this.invulnerable = invulnerable;
    }

    public static BossAttack byId(int id) {
        BossAttack[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
