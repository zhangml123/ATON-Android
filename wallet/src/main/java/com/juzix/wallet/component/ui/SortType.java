package com.juzix.wallet.component.ui;

import com.juzhen.framework.util.NumberParserUtils;
import com.juzix.wallet.App;
import com.juzix.wallet.entity.CandidateEntity;
import com.juzix.wallet.utils.BigDecimalUtil;
import com.juzix.wallet.utils.LanguageUtil;

import java.util.Comparator;
import java.util.Locale;

/**
 * @author matrixelement
 */

public enum SortType {

    SORTED_BY_DEFAULT {
        @Override
        public Comparator<CandidateEntity> getComparator() {
            return new DefaultComparator();
        }
    }, SORTED_BY_REGION {
        @Override
        public Comparator<CandidateEntity> getComparator() {
            return new RegionComparator();
        }
    }, SORTED_BY_REWARD {
        @Override
        public Comparator<CandidateEntity> getComparator() {
            return new RewardComparator();
        }
    };

    public abstract Comparator<CandidateEntity> getComparator();

    static class RewardComparator implements Comparator<CandidateEntity> {
        @Override
        public int compare(CandidateEntity o1, CandidateEntity o2) {
            int compare = Long.compare(NumberParserUtils.parseLong(o2.getReward()), NumberParserUtils.parseLong(o1.getReward()));
            if (compare != 0) {
                return compare;
            }
            compare = Integer.compare(o1.getNodeType().ordinal(), o2.getNodeType().ordinal());
            if (compare != 0) {
                return compare;
            }
            compare = Double.compare(NumberParserUtils.parseDouble(o2.getDeposit()), NumberParserUtils.parseDouble(o1.getDeposit()));
            if (compare != 0) {
                return compare;
            }
            compare = Long.compare(NumberParserUtils.parseLong(o2.getTicketCount()), NumberParserUtils.parseLong(o1.getTicketCount()));
            if (compare != 0) {
                return compare;
            }
            return Long.compare(o1.getJoinTime(), o2.getJoinTime());
        }
    }

    static class RegionComparator implements Comparator<CandidateEntity> {
        @Override
        public int compare(CandidateEntity o1, CandidateEntity o2) {
            if (o1.getCountryEntity().isNull()) {
                return 0;
            }
            if (o2.getCountryEntity().isNull()) {
                return 1;
            }

            if (Locale.CHINESE.getLanguage().equals(LanguageUtil.getLocale(App.getContext()).getLanguage())) {
                return o1.getCountryEntity().getZhPinyinName().compareToIgnoreCase(o2.getCountryEntity().getZhPinyinName());
            } else {
                return o1.getCountryEntity().getEnName().compareToIgnoreCase(o2.getCountryEntity().getEnName());
            }
        }
    }

    static class DefaultComparator implements Comparator<CandidateEntity> {
        @Override
        public int compare(CandidateEntity o1, CandidateEntity o2) {

            double o1Amount = BigDecimalUtil.add(o1.getDeposit(), BigDecimalUtil.mul(String.valueOf(o1.getTicketCount()), o1.getTicketPrice()).toPlainString());
            double o2Amount = BigDecimalUtil.add(o2.getDeposit(), BigDecimalUtil.mul(String.valueOf(o2.getTicketCount()), o2.getTicketPrice()).toPlainString());

            return Double.compare(o2Amount, o1Amount);
        }
    }
}



