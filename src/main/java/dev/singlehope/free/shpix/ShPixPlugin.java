package dev.singlehope.free.shpix;

import dev.singlehope.free.shpix.command.ShPixCommand;
import dev.singlehope.free.shpix.command.ShopCommand;
import dev.singlehope.free.shpix.config.Messages;
import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.coupon.CouponChatListener;
import dev.singlehope.free.shpix.coupon.CouponInputService;
import dev.singlehope.free.shpix.coupon.CouponService;
import dev.singlehope.free.shpix.menu.MenuListener;
import dev.singlehope.free.shpix.notification.DiscordNotifier;
import dev.singlehope.free.shpix.payment.OrderItems;
import dev.singlehope.free.shpix.payment.OrderService;
import dev.singlehope.free.shpix.payment.PaymentPoller;
import dev.singlehope.free.shpix.payment.RewardService;
import dev.singlehope.free.shpix.payment.gateway.GatewayRegistry;
import dev.singlehope.free.shpix.payment.listener.OrderItemListener;
import dev.singlehope.free.shpix.qrcode.QrCodeImage;
import dev.singlehope.free.shpix.qrcode.QrCodeService;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.shop.ShopCatalog;
import dev.singlehope.free.shpix.shop.ShopLoader;
import dev.singlehope.free.shpix.storage.Database;
import dev.singlehope.free.shpix.storage.OrderRepository;
import dev.singlehope.free.shpix.storage.SchemaManager;
import dev.singlehope.free.shpix.storage.UserRepository;
import dev.singlehope.free.shpix.user.UserListener;
import dev.singlehope.free.shpix.user.UserService;
import dev.singlehope.free.shpix.util.Placeholders;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShPixPlugin extends JavaPlugin {

    private final AtomicBoolean storageReady = new AtomicBoolean();

    private PluginConfig pluginConfig;
    private Messages messages;
    private Database database;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private UserService userService;
    private ShopCatalog catalog;
    private ShopLoader shopLoader;
    private GatewayRegistry gateways;
    private DiscordNotifier discord;
    private RewardService rewardService;
    private OrderService orderService;
    private PaymentPoller poller;
    private CouponService couponService;
    private CouponInputService couponInputService;
    private QrCodeService qrCodeService;
    private Schedulers.Task storageTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        OrderItems.init(this);
        Placeholders.detect();

        this.pluginConfig = PluginConfig.load(getConfig(), getLogger());
        this.messages = new Messages(this);

        this.database = new Database(getLogger());
        this.orderRepository = new OrderRepository(this.database, this.pluginConfig.tablePrefix());
        this.userRepository = new UserRepository(this.database, this.pluginConfig.tablePrefix());
        this.userService = new UserService(this.database, this.userRepository);

        this.catalog = new ShopCatalog();
        this.shopLoader = new ShopLoader(this);
        this.shopLoader.load(this.catalog, this.pluginConfig);

        this.gateways = new GatewayRegistry(getLogger());
        this.gateways.load(this.pluginConfig, getConfig());

        this.discord = new DiscordNotifier(this, this.pluginConfig);
        this.rewardService = new RewardService(this, this.catalog, this.messages, this.discord);
        this.orderService = new OrderService(this, this.pluginConfig, this.database, this.orderRepository,
                this.userRepository, this.gateways, this.rewardService, this.userService);

        this.couponService = new CouponService(this, this.pluginConfig);
        this.couponInputService = new CouponInputService();
        this.qrCodeService = new QrCodeService(this, this.messages);
        this.orderService.setWaitingHandler((player, order) ->
                this.qrCodeService.deliverQrCode(player, order, delivered -> {
                }));

        registerListeners();
        registerCommands();

        this.poller = new PaymentPoller(this, this.orderService, this.couponInputService);
        this.poller.start(this.pluginConfig.pollInterval());

        this.storageTask = Schedulers.asyncTimer(this, this::initialiseStorage, 1L, 60_000L);
        getLogger().info("ShPIX habilitado" + (Schedulers.isFolia() ? " (Folia)" : "") + ".");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new UserListener(this), this);
        getServer().getPluginManager().registerEvents(new OrderItemListener(), this);
        getServer().getPluginManager().registerEvents(new CouponChatListener(this), this);
    }

    private void registerCommands() {
        bind("shop", new ShopCommand(this));
        bind("shpix", new ShPixCommand(this));
    }

    private void bind(final String name, final org.bukkit.command.TabExecutor executor) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Comando /" + name + " não declarado em plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void initialiseStorage() {
        if (this.storageReady.get()) {
            return;
        }
        if (!this.database.connect(this.pluginConfig)) {
            return;
        }
        try {
            new SchemaManager(this.database, this.pluginConfig.tablePrefix(), getLogger()).apply();
        } catch (SQLException exception) {
            getLogger().severe("Não foi possível preparar as tabelas do banco de dados; nova tentativa em 60s.");
            return;
        }
        if (!this.storageReady.compareAndSet(false, true)) {
            return;
        }
        this.orderService.loadPending();
        Schedulers.global(this, () -> getServer().getOnlinePlayers().forEach(player -> {
            final java.util.UUID id = player.getUniqueId();
            final String name = player.getName();
            Schedulers.async(this, () -> this.userService.load(id, name));
            this.orderService.deliverPendingFor(player);
        }));
        getLogger().info("Banco de dados conectado.");
        if (this.storageTask != null) {
            this.storageTask.cancel();
            this.storageTask = null;
        }
    }

    public void reloadEverything() {
        final String previousJdbc = this.pluginConfig.jdbcUrl();
        final String previousPrefix = this.pluginConfig.tablePrefix();
        reloadConfig();
        this.pluginConfig = PluginConfig.load(getConfig(), getLogger());
        if (!previousJdbc.equals(this.pluginConfig.jdbcUrl()) || !previousPrefix.equals(this.pluginConfig.tablePrefix())) {
            getLogger().warning("Alterações na seção 'database' só entram em vigor após reiniciar o servidor.");
        }
        this.messages.reload();
        QrCodeImage.reset();
        this.shopLoader.load(this.catalog, this.pluginConfig);
        this.gateways.load(this.pluginConfig, getConfig());
        this.discord.reload(this.pluginConfig);
        this.orderService.updateConfig(this.pluginConfig);
        this.couponService.reload(this.pluginConfig);
        this.poller.start(this.pluginConfig.pollInterval());
    }

    @Override
    public void onDisable() {
        if (this.poller != null) {
            this.poller.stop();
        }
        if (this.storageTask != null) {
            this.storageTask.cancel();
            this.storageTask = null;
        }
        if (this.orderService != null) {
            this.orderService.shutdown();
        }
        if (this.couponInputService != null) {
            this.couponInputService.clear();
        }
        if (this.userService != null) {
            this.userService.clear();
        }
        if (this.discord != null) {
            this.discord.close();
        }
        if (this.gateways != null) {
            this.gateways.close();
        }
        if (this.database != null) {
            this.database.close();
        }
    }

    public PluginConfig pluginConfig() {
        return this.pluginConfig;
    }

    public Messages messages() {
        return this.messages;
    }

    public ShopCatalog catalog() {
        return this.catalog;
    }

    public OrderService orders() {
        return this.orderService;
    }

    public CouponService coupons() {
        return this.couponService;
    }

    public CouponInputService couponInput() {
        return this.couponInputService;
    }

    public QrCodeService qrCodes() {
        return this.qrCodeService;
    }

    public UserService users() {
        return this.userService;
    }

    public GatewayRegistry gateways() {
        return this.gateways;
    }

    public boolean isStorageReady() {
        return this.storageReady.get();
    }
}
