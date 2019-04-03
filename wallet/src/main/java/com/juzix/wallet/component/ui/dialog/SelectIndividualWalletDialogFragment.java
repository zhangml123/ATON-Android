package com.juzix.wallet.component.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.jakewharton.rxbinding3.widget.RxAdapterView;
import com.juzix.wallet.R;
import com.juzix.wallet.app.Constants;
import com.juzix.wallet.component.adapter.SelectIndividualWalletListAdapter;
import com.juzix.wallet.component.widget.ShadowDrawable;
import com.juzix.wallet.db.entity.IndividualWalletInfoEntity;
import com.juzix.wallet.db.sqlite.IndividualWalletInfoDao;
import com.juzix.wallet.engine.IndividualWalletTransactionManager;
import com.juzix.wallet.entity.IndividualWalletEntity;
import com.juzix.wallet.entity.WalletEntity;
import com.juzix.wallet.utils.DensityUtil;

import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * @author matrixelement
 */
public class SelectIndividualWalletDialogFragment extends BaseDialogFragment {

    public final static String CREATE_SHARED_WALLET = "create_shared_wallet";
    public final static String SELECT_SHARED_WALLET_OWNER = "create_shared_wallet_owner";
    public final static String SELECT_TRANSACTION_WALLET = "select_transaction_wallet";
    public final static String SELECT_UNLOCK_WALLET = "select_unlock_wallet";

    @BindView(R.id.list_wallet)
    ListView listWallet;
    @BindView(R.id.layout_content)
    LinearLayout layoutContent;

    private Unbinder unbinder;
    private SelectIndividualWalletListAdapter selectWalletListAdapter;
    private OnItemClickListener mListener;

    public static SelectIndividualWalletDialogFragment newInstance(String uuid) {
        SelectIndividualWalletDialogFragment dialogFragment = new SelectIndividualWalletDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(Constants.Bundle.BUNDLE_UUID, uuid);
        bundle.putBoolean(Constants.Bundle.BUNDLE_FEE_AMOUNT, false);
        dialogFragment.setArguments(bundle);
        return dialogFragment;
    }


    public static SelectIndividualWalletDialogFragment newInstance(String uuid, boolean needAmount) {
        SelectIndividualWalletDialogFragment dialogFragment = new SelectIndividualWalletDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(Constants.Bundle.BUNDLE_UUID, uuid);
        bundle.putBoolean(Constants.Bundle.BUNDLE_FEE_AMOUNT, needAmount);
        dialogFragment.setArguments(bundle);
        return dialogFragment;
    }

    public SelectIndividualWalletDialogFragment setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener;
        return this;
    }

    @Override
    protected Dialog onCreateDialog(Dialog baseDialog) {
        View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_fragment_select_wallet, null, false);
        baseDialog.setContentView(contentView);
        setFullWidthEnable(true);
        setGravity(Gravity.BOTTOM);
        setAnimation(R.style.Animation_slide_in_bottom);
        setHorizontalMargin(DensityUtil.dp2px(getContext(), 14));
        setyOffset(DensityUtil.dp2px(getContext(), 4));
        unbinder = ButterKnife.bind(this, contentView);
        initViews();
        return baseDialog;
    }

    private void initViews() {

        ShadowDrawable.setShadowDrawable(layoutContent,
                ContextCompat.getColor(context, R.color.color_ffffff),
                DensityUtil.dp2px(context, 6f),
                ContextCompat.getColor(getContext(), R.color.color_33616161)
                , DensityUtil.dp2px(context, 10f),
                0,
                DensityUtil.dp2px(context, 2f));

        selectWalletListAdapter = new SelectIndividualWalletListAdapter(R.layout.item_select_wallet_list, null, listWallet, getTag());
        listWallet.setAdapter(selectWalletListAdapter);

        RxAdapterView.itemClicks(listWallet)
                .throttleFirst(500, TimeUnit.MILLISECONDS)
                .compose(bindToLifecycle())
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer position) throws Exception {
                        IndividualWalletEntity walletEntity = selectWalletListAdapter.getItem(position);
                        selectWalletListAdapter.notifyDataSetChanged();
                        dismiss();
                        if (mListener != null) {
                            mListener.onItemClick(walletEntity);
                        }
                    }
                });

        loadWalletList();

    }

    private void loadWalletList() {

        Flowable.fromCallable(new Callable<List<IndividualWalletInfoEntity>>() {
            @Override
            public List<IndividualWalletInfoEntity> call() throws Exception {
                return IndividualWalletInfoDao.getInstance().getWalletInfoList();
            }
        })
                .flatMap(new Function<List<IndividualWalletInfoEntity>, Publisher<IndividualWalletInfoEntity>>() {
                    @Override
                    public Publisher<IndividualWalletInfoEntity> apply(List<IndividualWalletInfoEntity> individualWalletInfoEntities) throws Exception {
                        return Flowable.fromIterable(individualWalletInfoEntities);
                    }
                })
                .compose(bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .map(new Function<IndividualWalletInfoEntity, IndividualWalletEntity>() {
                    @Override
                    public IndividualWalletEntity apply(IndividualWalletInfoEntity walletInfoEntity) throws Exception {
                        return walletInfoEntity.buildWalletEntity();
                    }
                })
                .map(new Function<IndividualWalletEntity, IndividualWalletEntity>() {

                    @Override
                    public IndividualWalletEntity apply(IndividualWalletEntity walletEntity) throws Exception {
                        return IndividualWalletTransactionManager.getInstance().getBalanceByAddress(walletEntity);
                    }
                })
                .toList()
                .onErrorReturnItem(new ArrayList<>())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BiConsumer<List<IndividualWalletEntity>, Throwable>() {
                    @Override
                    public void accept(List<IndividualWalletEntity> objects, Throwable throwable) throws Exception {
                        if (objects != null && !objects.isEmpty()) {
                            String uuid = getArguments().getString(Constants.Bundle.BUNDLE_UUID);
                            boolean needAmount = getArguments().getBoolean(Constants.Bundle.BUNDLE_FEE_AMOUNT);
                            List<IndividualWalletEntity> newWalletEntityList = new ArrayList<>();
                            if (needAmount){
                                for (IndividualWalletEntity walletEntity : objects){
                                    if (walletEntity.getBalance() > 0){
                                        newWalletEntityList.add(walletEntity);
                                    }
                                }
                            }else {
                                newWalletEntityList.addAll(objects);
                            }
                            Collections.sort(newWalletEntityList, new Comparator<WalletEntity>() {
                                @Override
                                public int compare(WalletEntity o1, WalletEntity o2) {
                                    return Long.compare(o1.getUpdateTime(),  o2.getUpdateTime());
                                }
                            });
                            selectWalletListAdapter.notifyDataChanged(newWalletEntityList);
                            listWallet.setItemChecked(newWalletEntityList.indexOf(new IndividualWalletEntity.Builder().uuid(uuid).build()), true);
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null) {
            unbinder.unbind();
        }
    }

    public interface OnItemClickListener {

        void onItemClick(IndividualWalletEntity walletEntity);
    }
}
