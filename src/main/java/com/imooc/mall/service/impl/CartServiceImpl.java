package com.imooc.mall.service.impl;

import com.google.gson.Gson;
import com.imooc.mall.dao.ProductMapper;
import com.imooc.mall.enums.ProductStatusEnum;
import com.imooc.mall.enums.ResponseEnum;
import com.imooc.mall.form.CartAddForm;
import com.imooc.mall.form.CartUpdateForm;
import com.imooc.mall.pojo.Cart;
import com.imooc.mall.pojo.Product;
import com.imooc.mall.service.ICartService;
import com.imooc.mall.vo.CartProductVo;
import com.imooc.mall.vo.CartVo;
import com.imooc.mall.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CartServiceImpl implements ICartService {

    private final static String CART_REDIS_KEY_TEMPLATE = "cart_%d";

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public ResponseVo<CartVo> add(Integer uid, CartAddForm form) {
        Integer quantity = 1;
        Product product = productMapper.selectByPrimaryKey(form.getProductId());

        //判断商品是否存在
        if (product == null) {
            return ResponseVo.error(ResponseEnum.PRODUCT_NOT_EXIST);
        }
        //商品是否是正常在售状态
        if (product.getStatus().equals(ProductStatusEnum.OFF_SALE.getCode()) || product.getStatus().equals(ProductStatusEnum.DELETE.getCode())) {
            return ResponseVo.error(ResponseEnum.PRODUCT_OFF_SALE_OR_DELETE);
        }

        //商品库存是否充足
        if (product.getStock() <= 0) {
            return ResponseVo.error(ResponseEnum.PRODUCT_STOCK_ERROR);
        }

        //写入到redis
        //key: cart_1
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();

        Cart cart = new Cart();
        String value = opsForHash.get(redisKey, String.valueOf(product.getId()));
        if (StringUtils.isEmpty(value)) {
            //redis中没有该商品
            cart = new Cart(product.getId(), quantity, form.getSelected());
        }else {
            //已经有了数量+1
            cart = new Gson().fromJson(value, Cart.class);
            cart.setQuantity(cart.getQuantity() + quantity);
        }

        opsForHash.put(redisKey,
                String.valueOf(product.getId()), new Gson().toJson(cart));

        return list(uid);
    }

    @Override
    public ResponseVo<CartVo> list(Integer uid) {
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();

        Map<String, String> entries = opsForHash.entries(redisKey);

        boolean selectAll = true;
        Integer cartTotalQuantity = 0;
        BigDecimal cartTotalPrice = BigDecimal.ZERO;
        CartVo cartVo = new CartVo();
        List<CartProductVo> cartProductVoList = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            Integer productId = Integer.valueOf(entry.getKey());
            Cart cart = new Gson().fromJson(entry.getValue(), Cart.class);
            //Todo 需要优化，使用mysql中的in
            Product product = productMapper.selectByPrimaryKey(productId);
            if (product != null) {
                CartProductVo cartProductVo = new CartProductVo(productId, cart.getQuantity(),
                        product.getName(), product.getSubtitle(), product.getMainImage(), product.getPrice(), product.getStatus(),
                        product.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())), product.getStock(), cart.getProductSelected());
                cartProductVoList.add(cartProductVo);
                if (!cart.getProductSelected()) {
                    selectAll = false;
                }
                if (cart.getProductSelected()) {
                    cartTotalPrice = cartTotalPrice.add(cartProductVo.getProductTotalPrice());
                }
            }
            cartTotalQuantity += cart.getQuantity();
        }

        //有一个没有选中，就不叫全选
        cartVo.setSelectedAll(selectAll);
        cartVo.setCartTotalPrice(cartTotalPrice);
        cartVo.setCartTotalQuantity(cartTotalQuantity);
        cartVo.setCartProductVoList(cartProductVoList);
        return ResponseVo.success(cartVo);
    }

    @Override
    public ResponseVo<CartVo> update(Integer uid, Integer productId, CartUpdateForm cartUpdateForm) {
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();

        String value = opsForHash.get(redisKey, String.valueOf(productId));
        if (StringUtils.isEmpty(value)) {
            //没有该商品，报错
            return ResponseVo.error(ResponseEnum.CART_PRODUCT_NOT_EXIST);
        }else {
            //已经有了，修改内容
            Cart cart = new Gson().fromJson(value, Cart.class);
            if (cartUpdateForm.getQuantity() != null && cartUpdateForm.getQuantity() > 0) {
                cart.setQuantity(cartUpdateForm.getQuantity());
            }
            if (cartUpdateForm.getSelected() != null) {
                cart.setProductSelected(cartUpdateForm.getSelected());
            }

            opsForHash.put(redisKey, String.valueOf(productId), new Gson().toJson(cart));
        }
        return list(uid);
    }

    @Override
    public ResponseVo<CartVo> delete(Integer uid, Integer productId) {
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();

        String value = opsForHash.get(redisKey, String.valueOf(productId));
        if (StringUtils.isEmpty(value)) {
            //没有该商品，报错
            return ResponseVo.error(ResponseEnum.CART_PRODUCT_NOT_EXIST);
        }
        opsForHash.delete(redisKey, String.valueOf(productId));
        return list(uid);
    }

    @Override
    public ResponseVo<CartVo> selectAll(Integer uid) {
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();

        for (Cart cart : listForCart(uid)) {
            cart.setProductSelected(true);
            opsForHash.put(redisKey, String.valueOf(cart.getProductId()), new Gson().toJson(cart));
        }
        return list(uid);
    }

    @Override
    public ResponseVo<CartVo> unSelectAll(Integer uid) {
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();

        for (Cart cart : listForCart(uid)) {
            cart.setProductSelected(false);
            opsForHash.put(redisKey, String.valueOf(cart.getProductId()), new Gson().toJson(cart));
        }
        return list(uid);
    }

    @Override
    public ResponseVo<Integer> sum(Integer uid) {
        Integer sum = listForCart(uid).stream().map(Cart::getQuantity).reduce(0, Integer::sum);
        return ResponseVo.success(sum);
    }

    private List<Cart> listForCart(Integer uid) {
        String redisKey = String.format(CART_REDIS_KEY_TEMPLATE, uid);
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();
        Map<String, String> entries = opsForHash.entries(redisKey);

        List<Cart> cartList = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            cartList.add(new Gson().fromJson(entry.getValue(), Cart.class));
        }
        return cartList;
    }
}
