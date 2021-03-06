package com.platon.aton.component.ui;

import android.os.Parcel;
import android.os.Parcelable;

import com.platon.aton.R;
import com.platon.aton.entity.VerifyNode;
import com.platon.aton.utils.BigIntegerUtil;
import com.platon.aton.utils.NumberParserUtils;

import java.util.Comparator;

/**
 * @author matrixelement
 */

public enum SortType implements Parcelable {

    SORTED_BY_NODE_RANKINGS {
        @Override
        public Comparator<VerifyNode> getComparator() {
            return new NodeRankingsComparator();
        }

        @Override
        public int getTextRes() {
            return R.string.msg_node_rankings;
        }

    }, SORTED_BY_DELEGATED_AMOUNT {
        @Override
        public Comparator<VerifyNode> getComparator() {
            return new DelegatedAmountComparator();
        }

        @Override
        public int getTextRes() {
            return R.string.msg_delegated_amount;
        }

    }, SORTED_BY_DELEGATOR_NUMBER {
        @Override
        public Comparator<VerifyNode> getComparator() {
            return new DelegatorNumberComparator();
        }

        @Override
        public int getTextRes() {
            return R.string.msg_delegator_number;
        }

    }, SORTED_BY_ANNUAL_YIELD {
        @Override
        public Comparator<VerifyNode> getComparator() {
            return new AnnualYieldComparator();
        }

        @Override
        public int getTextRes() {
            return R.string.msg_annual_yield;
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }

    public static final Creator<SortType> CREATOR = new Creator<SortType>() {
        @Override
        public SortType createFromParcel(Parcel in) {
            return SortType.values()[in.readInt()];
        }

        @Override
        public SortType[] newArray(int size) {
            return new SortType[size];
        }
    };

    public abstract Comparator<VerifyNode> getComparator();

    public abstract int getTextRes();

    static class NodeRankingsComparator implements Comparator<VerifyNode> {

        @Override
        public int compare(VerifyNode o1, VerifyNode o2) {
            return Integer.compare(o1.getRanking(), o2.getRanking());
        }
    }

    static class DelegatedAmountComparator implements Comparator<VerifyNode> {

        @Override
        public int compare(VerifyNode o1, VerifyNode o2) {
            return BigIntegerUtil.toBigInteger(o2.getDelegateSum()).compareTo(BigIntegerUtil.toBigInteger(o1.getDelegateSum()));
        }
    }

    static class DelegatorNumberComparator implements Comparator<VerifyNode> {

        @Override
        public int compare(VerifyNode o1, VerifyNode o2) {
            return Long.compare(NumberParserUtils.parseLong(o2.getDelegate()), NumberParserUtils.parseLong(o1.getDelegate()));
        }
    }

    static class AnnualYieldComparator implements Comparator<VerifyNode> {

        @Override
        public int compare(VerifyNode o1, VerifyNode o2) {
            return Long.compare(NumberParserUtils.parseLong(o2.getDelegatedRatePA()), NumberParserUtils.parseLong(o1.getDelegatedRatePA()));
        }
    }
}



