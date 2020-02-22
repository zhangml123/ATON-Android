package com.juzix.wallet.component.adapter;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.juzix.wallet.R;
import com.juzix.wallet.component.adapter.base.BaseViewHolder;
import com.juzix.wallet.component.ui.view.TransactionRecordsActivity;
import com.juzix.wallet.component.widget.PendingAnimationLayout;
import com.juzix.wallet.entity.Transaction;
import com.juzix.wallet.entity.TransactionStatus;
import com.juzix.wallet.entity.TransactionType;
import com.juzix.wallet.entity.TransferType;
import com.juzix.wallet.utils.AmountUtil;
import com.juzix.wallet.utils.BigDecimalUtil;

import java.util.List;

public class TransactionViewHolder extends BaseViewHolder<Transaction> {

    private LinearLayout mTransactionStatusLayout;
    private PendingAnimationLayout mPendingLayout;
    private ImageView mTransactionStatusIv;
    private TextView mTransactionAmountTv;
    private TextView mTransactionStatusTv;
    private TextView mTransactionTimeTv;

    private List<String> mQueryAddressList;

    public TransactionViewHolder(int viewId, ViewGroup parent, List<String> queryAddressList) {
        super(viewId, parent);

        mQueryAddressList = queryAddressList;

        mTransactionStatusLayout = itemView.findViewById(R.id.layout_transaction_status);
        mPendingLayout = itemView.findViewById(R.id.layout_pending);
        mTransactionStatusIv = itemView.findViewById(R.id.iv_transaction_status);
        mTransactionAmountTv = itemView.findViewById(R.id.tv_transaction_amount);
        mTransactionStatusTv = itemView.findViewById(R.id.tv_transaction_status);
        mTransactionTimeTv = itemView.findViewById(R.id.tv_transaction_time);
    }

    public void setQueryAddressList(List<String> queryAddressList) {
        this.mQueryAddressList = queryAddressList;
    }

    @Override
    public void refreshData(Transaction transaction, int position) {
        super.refreshData(transaction, position);
        refreshData(transaction);
    }

    @Override
    public void updateItem(Bundle bundle) {
        if (bundle.isEmpty()) {
            return;
        }

        for (String key : bundle.keySet()) {
            switch (key) {
                case TransactionDiffCallback.KEY_TRANSACTION:
                    refreshData(bundle.getParcelable(key));
                    break;
                default:
                    break;
            }
        }
    }

    private void refreshData(Transaction transaction) {
        if (transaction == null) {
            return;
        }

        TransactionStatus transactionStatus = transaction.getTxReceiptStatus();
        TransactionType transactionType = transaction.getTxType();
        boolean isSender = transaction.isSender(mQueryAddressList);
        boolean isTransfer = transaction.isTransfer(mQueryAddressList);
        boolean isValueZero = !BigDecimalUtil.isBiggerThanZero(transaction.getValue());
        boolean isSend = isSender && transactionType != TransactionType.UNDELEGATE && transactionType != TransactionType.EXIT_VALIDATOR && transactionType != TransactionType.CLAIM_REWARDS;
        boolean isTransactionGray = isTransfer || isValueZero || transactionStatus == TransactionStatus.FAILED || transactionStatus == TransactionStatus.TIMEOUT;

        if (isTransactionGray) {
            mTransactionAmountTv.setText(AmountUtil.formatAmountText(transaction.getValue()));
            mTransactionAmountTv.setTextColor(ContextCompat.getColor(mContext, R.color.color_b6bbd0));
        } else if (isSend) {
            mTransactionAmountTv.setText(String.format("%s%s", "-", AmountUtil.formatAmountText(transaction.getValue())));
            mTransactionAmountTv.setTextColor(ContextCompat.getColor(mContext, R.color.color_ff3b3b));
        } else {
            mTransactionAmountTv.setText(String.format("%s%s", "+", AmountUtil.formatAmountText(transaction.getValue())));
            mTransactionAmountTv.setTextColor(ContextCompat.getColor(mContext, R.color.color_19a20e));
        }
        mTransactionStatusTv.setTextColor(isTransactionGray ? ContextCompat.getColor(mContext, R.color.color_000000_50) : ContextCompat.getColor(mContext, R.color.color_000000));
        mTransactionTimeTv.setTextColor(isTransactionGray ? ContextCompat.getColor(mContext, R.color.color_61646e_50) : ContextCompat.getColor(mContext, R.color.color_61646e));
        mTransactionStatusTv.setText(getTxTDesc(transaction, mContext, isSender));
        mTransactionTimeTv.setText(transaction.getShowCreateTime());
        mPendingLayout.setVisibility(transactionStatus != TransactionStatus.PENDING || mContext instanceof TransactionRecordsActivity ? View.GONE : View.VISIBLE);
        mTransactionStatusIv.setVisibility(transactionStatus == TransactionStatus.PENDING || mContext instanceof TransactionRecordsActivity ? View.GONE : View.VISIBLE);

        if (transactionType == TransactionType.TRANSFER) {
            mTransactionStatusIv.setImageResource(isSender ? R.drawable.icon_send_transation : R.drawable.icon_receive_transaction);
        } else {
            mTransactionStatusIv.setImageResource(isSend ? R.drawable.icon_delegate : R.drawable.icon_undelegate);
        }
    }

    private String getTxTDesc(Transaction transaction, Context context, boolean isSender) {
        TransactionType transactionType = transaction.getTxType();
        @TransferType int transferType = transaction.getTransferType(mQueryAddressList);
        if (transactionType == TransactionType.TRANSFER) {
            return context.getResources().getString(transferType == TransferType.TRANSFER ? R.string.transfer : isSender ? R.string.sent : R.string.received);
        } else {
            return context.getResources().getString(transactionType.getTxTypeDescRes());
        }
    }
}
