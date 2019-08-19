package com.juzix.wallet.component.ui.presenter;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import com.juzhen.framework.network.ApiRequestBody;
import com.juzhen.framework.network.ApiResponse;
import com.juzhen.framework.network.ApiSingleObserver;
import com.juzhen.framework.util.NumberParserUtils;
import com.juzix.wallet.R;
import com.juzix.wallet.component.adapter.WithDrawPopWindowAdapter;
import com.juzix.wallet.component.ui.base.BasePresenter;
import com.juzix.wallet.component.ui.contract.WithDrawContract;
import com.juzix.wallet.component.ui.dialog.InputWalletPasswordDialogFragment;
import com.juzix.wallet.component.ui.dialog.SelectWalletDialogFragment;
import com.juzix.wallet.engine.DelegateManager;
import com.juzix.wallet.engine.NodeManager;
import com.juzix.wallet.engine.ServerUtils;
import com.juzix.wallet.engine.WalletManager;
import com.juzix.wallet.engine.Web3jManager;
import com.juzix.wallet.entity.Wallet;
import com.juzix.wallet.entity.WithDrawBalance;
import com.juzix.wallet.utils.BigDecimalUtil;
import com.juzix.wallet.utils.RxUtils;

import org.web3j.crypto.Credentials;
import org.web3j.platon.BaseResponse;
import org.web3j.platon.TransactionCallback;
import org.web3j.platon.contracts.DelegateContract;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.PlatonSendTransaction;
import org.web3j.tx.PlatOnContract;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class WithDrawPresenter extends BasePresenter<WithDrawContract.View> implements WithDrawContract.Presenter {
    private Wallet mWallet;
    private String mNodeAddress;
    private String mNodeName;
    private String mNodeIcon;
    private String mBlockNum;
    private String mWalletAddress;

    //    Map<String, String> stringMap = new LinkedHashMap<>();
    private List<WithDrawBalance> list = new ArrayList<>();

    //    private double delegatedSum = 0; //已委托 (已锁定+未锁定)
    private int tag = 0;

    public WithDrawPresenter(WithDrawContract.View view) {
        super(view);
        mNodeAddress = view.getNodeAddressFromIntent();
        mNodeName = view.getNodeNameFromIntent();
        mNodeIcon = view.getNodeIconFromIntent();
        mBlockNum = view.getBlockNumFromIntent();
        mWalletAddress = view.getWalletAddressFromIntent();
    }


    @Override
    public void showWalletInfo() {
        getView().showNodeInfo(mNodeAddress, mNodeName, mNodeIcon);
        showSelectedWalletInfo();
    }

//    @Override
//    public void showSelectWalletDialogFragment() {
//
//        SelectWalletDialogFragment.newInstance(mWallet != null ? mWallet.getUuid() : "", true)
//                .setOnItemClickListener(new SelectWalletDialogFragment.OnItemClickListener() {
//                    @Override
//                    public void onItemClick(Wallet walletEntity) {
//                        if (isViewAttached()) {
//                            mWallet = walletEntity;
//                            getView().showSelectedWalletInfo(walletEntity);
//                        }
//                    }
//                })
//                .show(currentActivity().getSupportFragmentManager(), "showSelectWalletDialog");
//    }


    private void showSelectedWalletInfo() {
//        mWallet = WalletManager.getInstance().getFirstValidIndividualWalletBalance();
        mWallet = WalletManager.getInstance().getWalletEntityByWalletAddress(mWalletAddress);//通过钱包地址获取钱包对象
        if (isViewAttached() && mWallet != null) {
            getView().showSelectedWalletInfo(mWallet);
        }
    }


    @Override
    public boolean checkWithDrawAmount(String withdrawAmount) {
        //检查赎回的数量
        double amount = NumberParserUtils.parseDouble(withdrawAmount);
        String errMsg = null;
        if (TextUtils.isEmpty(withdrawAmount)) {
            errMsg = string(R.string.transfer_amount_cannot_be_empty);
        } else if (amount < 10) {
            //按钮不可点击,并且下方提示
            getView().showTips(true);
            updateWithDrawButtonState();
        } else {
            getView().showTips(false);
            updateWithDrawButtonState();
        }

        getView().showAmountError(errMsg);

        return TextUtils.isEmpty(errMsg);


    }

    @Override
    public void getBalanceType(String addr, String stakingBlockNum) {
        ServerUtils.getCommonApi().getWithDrawBalance(NodeManager.getInstance().getChainId(), ApiRequestBody.newBuilder()
                .put("addr", "0x493301712671ada506ba6ca7891f436d29185821") //todo 暂时写假的数据
                .put("nodeId", "0xa188edb6776931b5f18e228028aaab0d57217772753ac8d5bdaae585a4440cc94520c3b6f617c5cf60725893bc04326c87b5211d4b1d6c100dfc09f2c70917d8")
                .build()).compose(RxUtils.getSingleSchedulerTransformer())
                .compose(bindToLifecycle())
                .subscribe(new ApiSingleObserver<List<WithDrawBalance>>() {
                    @Override
                    public void onApiSuccess(List<WithDrawBalance> balanceList) {
                        if (isViewAttached()) {
                            list.clear();
                            list.addAll(balanceList);

                            if (null != balanceList && balanceList.size() > 0) {
                                double lockedSum = 0;
                                double unLockedSum = 0;
                                double releasedSum = 0;
                                double delegatedSum = 0;
                                for (WithDrawBalance balance : balanceList) {
                                    lockedSum += NumberParserUtils.parseDouble(balance.getShowLocked());
                                    unLockedSum += NumberParserUtils.parseDouble(balance.getShowUnLocked());
                                    releasedSum += NumberParserUtils.parseDouble(balance.getShowReleased());
                                }

                                delegatedSum = lockedSum + unLockedSum;
                                getView().showBalanceType(delegatedSum, unLockedSum, releasedSum);

//                                double locked = NumberParserUtils.parseDouble(NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(withDrawBalance.getLocked(), "1E18"))); //已锁定
//                                double unLocked = NumberParserUtils.parseDouble(NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(withDrawBalance.getUnLocked(), "1E18")));//未锁定
//                                double released = NumberParserUtils.parseDouble(NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(withDrawBalance.getReleased(), "1E18")));//已解除
//                                double delegated = locked + unLocked; //已委托 = 已锁定+未锁定
//
//                                stringMap.put("tag_delegated", String.valueOf(delegated));
//                                stringMap.put("tag_unlocked", String.valueOf(unLocked));
//                                stringMap.put("tag_released", String.valueOf(released));
//                                getView().showBalanceType(withDrawBalance, stringMap);

                            }
                        }

                    }

                    @Override
                    public void onApiFailure(ApiResponse response) {

                    }
                });
    }

    @SuppressLint("CheckResult")
    @Override
    public void submitWithDraw(String type) {
        Single.fromCallable(new Callable<Double>() {
            @Override
            public Double call() throws Exception {
//                return null; //获取输入数量
                return 100.00;
            }
        }).map(new Function<Double, Double>() {
            @Override
            public Double apply(Double aDouble) throws Exception {
                return aDouble;
            }
        }).compose(bindToLifecycle())
                .subscribe(new Consumer<Double>() {
                    @Override
                    public void accept(Double aDouble) throws Exception {
                        //把输入的数量和选择的数量做判断
                        if (isViewAttached()) {
                            double chooseNum = NumberParserUtils.parseDouble(getView().getChooseType());
                            if (chooseNum < aDouble) {
                                showLongToast(R.string.withdraw_operation_tips);
                                return;
                            }

                            String inputAmount = getView().getInputAmount();

                            InputWalletPasswordDialogFragment
                                    .newInstance(mWallet)
                                    .setOnWalletPasswordCorrectListener(new InputWalletPasswordDialogFragment.OnWalletPasswordCorrectListener() {
                                        @Override
                                        public void onWalletPasswordCorrect(Credentials credentials) {
//                                            withdraw(credentials, mNodeAddress, mBlockNum, inputAmount, type);
                                            withDrawIterate(credentials, mNodeAddress, inputAmount, type);
                                        }
                                    })
                                    .show(currentActivity().getSupportFragmentManager(), "inputWalletPasssword");
                        }
                    }
                });

    }

    /**
     * 输入的数量
     *
     * @param credentials
     * @param nodeId
     * @param withdrawAmount 输入数量
     * @param type           从  已委托 或 未锁定委托 提取：  只操作记录最新的那个委托对象（快高最大）
     *                       从  已解除委托 提取：  查看记录中所有 已解锁余额非0的记录。  会存在调用多次底层的情况。
     */
    public void withDrawIterate(Credentials credentials, String nodeId, String withdrawAmount, String type) {

        String stakingBlockNum = "0";
        if (TextUtils.equals(type, WithDrawPopWindowAdapter.TAG_DELEGATED) || TextUtils.equals(type, WithDrawPopWindowAdapter.TAG_UNLOCKED)) { //已委托 || 未锁定

            for (int i = 0; i < list.size(); i++) {
                if (!TextUtils.isEmpty(list.get(i).getLocked()) && !TextUtils.isEmpty(list.get(i).getUnLocked())) {
                    stakingBlockNum = list.get(i).getStakingBlockNum(); //这里其实就是第一条，因为最新的块高排在前面
                    withdraw(credentials, nodeId, stakingBlockNum, withdrawAmount, type);
                    return;
                }
            }
        } else { //表示选择的是已解除委托
            for (int i = 0; i < list.size(); i++) {
                WithDrawBalance balance = list.get(i);
                if (!TextUtils.isEmpty(balance.getReleased())) {
                    stakingBlockNum = balance.getStakingBlockNum();
                    tag++;
                    withdraw(credentials, nodeId, stakingBlockNum, balance.getReleased(), type); //这里传入的数量应该是记录中每个对象不为0的数量（而不是显示的全部数量，因为这里要循环执行多次）
                }
            }


        }
    }


    //操作赎回
    @SuppressLint("CheckResult")
    public void withdraw(Credentials credentials, String nodeId, String blockNum, String withdrawAmount, String type) {
        DelegateManager.getInstance().withdraw(credentials, nodeId, blockNum, withdrawAmount)
                .compose(RxUtils.bindToLifecycle(getView()))
                .compose(RxUtils.getSingleSchedulerTransformer())
                .subscribe(new Consumer<PlatonSendTransaction>() {
                    @Override
                    public void accept(PlatonSendTransaction platonSendTransaction) throws Exception {
                        if (!TextUtils.isEmpty(platonSendTransaction.getTransactionHash())) {
                            //操作成功，跳转到交易详情，当前页面关闭

                            if (TextUtils.equals(type, WithDrawPopWindowAdapter.TAG_DELEGATED) || TextUtils.equals(type, WithDrawPopWindowAdapter.TAG_UNLOCKED)) {
                                //交易手续费还没拿
                                getView().withDrawSuccessInfo(PlatOnContract.DELEGATE_CONTRACT_ADDRESS, mWalletAddress, 0, "1005", list.get(0).getReleased(),
                                        "", mNodeName, mNodeAddress, 2);

                            } else {
                                if (tag == list.size()) {
                                    //交易手续费还没拿
                                    getView().withDrawSuccessInfo(PlatOnContract.DELEGATE_CONTRACT_ADDRESS, mWalletAddress, 0, "1005", list.get(0).getReleased(),
                                            "", mNodeName, mNodeAddress, 2);
                                }

                            }

                        }

                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        if (isViewAttached()) {
                            showLongToast(R.string.delegate_failed);
                        }
                    }
                });


//        Web3j web3j = Web3jManager.getInstance().getWeb3j();
//        String chainId = NodeManager.getInstance().getChainId();
//        DelegateContract delegateContract = DelegateContract.load(web3j, credentials, new DefaultGasProvider(), chainId);
//        delegateContract.asyncUnDelegate(nodeId, new BigInteger(blockNum), new BigInteger(withdrawAmount), new TransactionCallback<BaseResponse>() {
//
//            @Override
//            public void onTransactionStart() {
//
//            }
//
//            @Override
//            public void onTransaction(PlatonSendTransaction sendTransaction) {
//
//            }
//
//            @Override
//            public void onTransactionSucceed(BaseResponse baseResponse) {
//                if (baseResponse != null && baseResponse.isStatusOk()) {
//
//                }
//
//            }
//
//            @Override
//            public void onTransactionFailed(BaseResponse baseResponse) {
//
//            }
//        });
    }


    @Override
    public void updateWithDrawButtonState() {
        if (isViewAttached()) {
            String withdrawAmount = getView().getWithDrawAmount();
            boolean isAmountValid = !TextUtils.isEmpty(withdrawAmount) && NumberParserUtils.parseDouble(withdrawAmount) > 10;
            getView().setWithDrawButtonState(isAmountValid);
        }
    }


}
