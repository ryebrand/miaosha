/**
 * Copyright (C), 2015-2019, XXX有限公司
 * FileName: ItemServiceImpl
 * Author:   俊哥
 * Date:     2019/6/12 17:24
 * Description:
 * History:
 * <author>          <time>          <version>          <desc>
 * 作者姓名           修改时间           版本号              描述
 */
package pers.jun.service.impl;

import org.apache.commons.lang3.StringUtils;
import pers.jun.controller.viewObject.ItemVo;
import pers.jun.dao.ItemMapper;
import pers.jun.dao.ItemScrollMapper;
import pers.jun.dao.ItemStockMapper;
import pers.jun.error.BusinessException;
import pers.jun.error.EmBusinessError;
import pers.jun.pojo.Item;
import pers.jun.pojo.ItemScroll;
import pers.jun.pojo.ItemStock;
import pers.jun.service.ItemService;
import pers.jun.service.PromoService;
import pers.jun.service.model.ItemModel;
import pers.jun.service.model.PromoModel;
import pers.jun.validation.ValidationResult;
import pers.jun.validation.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 〈一句话功能简述〉<br> 
 * 〈〉
 *
 * @author 俊哥
 * @create 2019/6/12
 * @since 1.0.0
 */
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemMapper itemMapper;

    @Autowired
    private ItemStockMapper stockMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private ItemScrollMapper itemScrollMapper;

    @Override
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if(result.isHasErr()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }

        //转换itemModel-item
        Item item = convertToItem(itemModel);

        //写入数据库
        itemMapper.insertSelective(item);
        itemModel.setItemId(item.getId());

        ItemStock stock = convertToItemStock(itemModel);
        stockMapper.insertSelective(stock);

        //返回创建完成的对象
        return getById(itemModel.getItemId());

    }


    /**
     * 查询所有
     * @return
     */
    public List<ItemModel> getList() {
        List<Item> itemList = itemMapper.selectList();
        List<ItemModel> itemModelList = converToItemModelList(itemList);

        return itemModelList;
    }

    /**
     * 通过分类查找
     * @return
     */
    public List<ItemModel> getByCategory(Integer categoryId) {
        List<Item> itemList = itemMapper.selectByCategory(categoryId);
        List<ItemModel> itemModelList = converToItemModelList(itemList);
        return itemModelList;

    }

    /**
     * 通过id查找
     * @param id
     * @return
     */
    public ItemModel getById(Integer id) {
        Item item = itemMapper.selectByPrimaryKey(id);
        if(item == null){
            return null;
        }

        //使用库存service得到库存
        ItemStock itemStock = stockMapper.selectByItemId(id);

        //转换相应的bean
        ItemModel itemModel = convertToItemModel(item,itemStock);

        //得到商品相应的活动
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getItemId());
        //表示存在还未结束的活动
        if(promoModel != null && promoModel.getStatus() != 3){
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;

    }

    /**
     * 得到名称和活动（订单展示）
     * @param id
     * @return
     */
    public ItemModel getNameAndPromo(Integer id) {
        Item item = itemMapper.selectByPrimaryKey(id);
        if(item == null){
            return null;
        }

        //转换相应的bean
        ItemModel itemModel = convertToItemModel(item);

        //得到商品相应的活动
        PromoModel promoModel = promoService.getPromoByItemId(id);
        //表示存在还未结束的活动
        if(promoModel != null && promoModel.getStatus() != 3){
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;

    }

    /**
     * 通过商品id更新stock
     * @param itemId
     * @param amount
     * @return
     */
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) {
        int affectLine = stockMapper.updateByItemId(itemId, amount);
        if(affectLine > 0)
            return true;
        return false;
    }

    /**
     * 更新销量
     * @param id
     * @param amount
     */
    public boolean increaseSales(Integer id, Integer amount) {
        int affectLine = itemMapper.increaseSales(id, amount);
        if(affectLine > 0){
            return true;
        }
        return false;
    }

    /**
     * 查询热门商品
     * @return
     */
    public List<ItemModel> getPopular(int count) {
        List<Item> list = itemMapper.getPopular(count);
        List<ItemModel> itemModels = converToItemModelList(list);
        return itemModels;
    }

    //根据活动进行查询商品
    public List<ItemModel> getPromoItems(List<PromoModel> promoItems) {
        List<ItemModel> list = new ArrayList<>();
        for (PromoModel promoItem : promoItems) {
            Item item = itemMapper.selectByPrimaryKey(promoItem.getItemId());
            if(item == null){
                return null;
            }

            //使用库存service得到库存
            ItemStock itemStock = stockMapper.selectByItemId(item.getId());

            //转换相应的bean
            ItemModel itemModel = convertToItemModel(item,itemStock);
            itemModel.setPromoModel(promoItem);
            list.add(itemModel);
        }
        return list;
    }

    /**
     * 查询图片轮播商品
     * @return
     */
    public List<ItemScroll> getHomeScroll() {
        List<ItemScroll> scrollList = itemScrollMapper.getList();
        return scrollList;
    }

    /**
     * 根据商品名模糊查找
     */
    public List<ItemModel> getListByName(String key) {
        return converToItemModelList(itemMapper.getListByName(key));
    }

    /**
     * bean转换
     * @param itemModel
     * @return
     */
    private Item convertToItem(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        Item item = new Item();
        BeanUtils.copyProperties(itemModel,item);
        item.setId(itemModel.getItemId());
        item.setPrice(new Double(String.valueOf(itemModel.getPrice())));
        return item;
    }

    /**
     * bean转换
     * @param itemModel
     * @return
     */
    private ItemStock convertToItemStock(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStock itemStock = new ItemStock();
        itemStock.setStock(itemModel.getStock());
        itemStock.setItemId(itemModel.getItemId());
        return itemStock;
    }

    /**
     * bean转换
     * @param item
     * @param item
     * @return
     */
    private ItemModel convertToItemModel(Item item){
        if(item == null){
            return null;
        }
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(item,itemModel);
        itemModel.setItemId(item.getId());
        itemModel.setPrice(new BigDecimal(item.getPrice()));
        return itemModel;
    }

    /**
     * bean转换
     * @param item
     * @param itemStock
     * @return
     */
    private ItemModel convertToItemModel(Item item,ItemStock itemStock){
        if(item == null || itemStock == null){
            return null;
        }
        ItemModel itemModel = convertToItemModel(item);
        itemModel.setStock(itemStock.getStock());
        return itemModel;
    }

    /**
     *
     */
    private List<ItemModel> converToItemModelList(List<Item> itemList){
        List<ItemModel> itemModelList = new ArrayList<>();
        for (Item item : itemList) {
            ItemStock stock = stockMapper.selectByItemId(item.getId());
            ItemModel itemModel = convertToItemModel(item, stock);
            //得到商品相应的活动
            PromoModel promoModel = promoService.getPromoByItemId(itemModel.getItemId());
            //表示存在还未结束的活动
            if(promoModel != null && promoModel.getStatus() != 3){
                itemModel.setPromoModel(promoModel);
            }
            itemModelList.add(itemModel);
        }
        return itemModelList;
    }

    /**
     * 得到itemList中处于活动中的商品并转换成itemModel
     */
    private List<ItemModel> converToItemModelPromo(List<Item> itemList){
        List<ItemModel> list = new ArrayList<>();
        for (Item item : itemList) {
            PromoModel promoModel = promoService.getPromoByItemId(item.getId());
            //表示存在还未结束的活动
            if(promoModel != null && promoModel.getStatus() != 3){
                ItemStock stock = stockMapper.selectByItemId(item.getId());
                ItemModel itemModel = convertToItemModel(item, stock);
                itemModel.setPromoModel(promoModel);
                list.add(itemModel);
            }
        }
        return list;
    }
}
