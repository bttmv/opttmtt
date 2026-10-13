package com.opengate.app.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opengate.app.data.repository.PlatformRepository
import com.opengate.app.data.repository.ListingRepository
import com.opengate.app.data.offline.OfflineSyncManager
import com.opengate.app.di.SupabaseProvider
import com.opengate.app.ui.screens.create.CreateListingScreen
import com.opengate.app.ui.screens.create.CreateListingViewModel
import com.opengate.app.ui.screens.auth.AuthScreen
import com.opengate.app.ui.screens.auth.AuthViewModel
import com.opengate.app.ui.screens.listing.ListingDetailScreen
import com.opengate.app.ui.screens.listing.ListingDetailViewModel
import com.opengate.app.ui.screens.listing.ListingsScreen
import com.opengate.app.ui.screens.listing.ListingsViewModel
import com.opengate.app.data.repository.ReviewRepository
import com.opengate.app.data.repository.AdminRepository
import com.opengate.app.data.repository.ChatRepository
import com.opengate.app.data.repository.ContentRepository
import com.opengate.app.data.repository.AuctionRepository
import com.opengate.app.ui.screens.auction.AuctionListScreen
import com.opengate.app.ui.screens.auction.AuctionListViewModel
import com.opengate.app.ui.screens.auction.AuctionScreen
import com.opengate.app.ui.screens.auction.AuctionViewModel
import com.opengate.app.ui.screens.admin.AdminScreen
import com.opengate.app.ui.screens.admin.AdminViewModel
import com.opengate.app.ui.screens.admin.AdminCatalogScreen
import com.opengate.app.ui.screens.admin.AdminCatalogViewModel
import com.opengate.app.ui.screens.admin.AdminContentScreen
import com.opengate.app.ui.screens.admin.AdminContentViewModel
import com.opengate.app.ui.screens.settings.SettingsScreen
import com.opengate.app.ui.screens.chat.ChatListScreen
import com.opengate.app.ui.screens.chat.ChatListViewModel
import com.opengate.app.ui.screens.chat.ChatScreen
import com.opengate.app.ui.screens.chat.ChatViewModel
import com.opengate.app.ui.screens.home.HomeScreen
import com.opengate.app.ui.screens.home.HomeViewModel
import com.opengate.app.ui.screens.splash.SplashScreen
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.gotrue.auth

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val HOME = "home"
    const val LISTINGS = "listings/{platformId}"
    const val DETAIL = "listing/{listingId}"
    const val CREATE = "create"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val ADMIN = "admin"
    const val ADMIN_MEMBERS = "admin/members"
    const val ADMIN_CATALOG = "admin/catalog"
    const val ADMIN_CONTENT = "admin/content"
    const val ADMIN_MODERATION = "admin/moderation"
    const val MY_LISTINGS = "my_listings"
    const val CHAT_LIST = "chats"
    const val CHAT = "chat/{conversationId}"
    const val AUCTION_LIST = "auctions"
    const val AUCTION = "auction/{auctionId}"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val client = SupabaseProvider.client(context)
    val platformRepo = PlatformRepository(client)

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            val scope = rememberCoroutineScope()
            SplashScreen(onFinished = {
                scope.launch {
                    // الانتظار حتى اكتمال تحميل الجلسة المحفوظة في SharedPreferences
                    runCatching { client.auth.awaitInitialization() }
                    val hasSession = client.auth.currentSessionOrNull() != null
                    val destination = if (hasSession) Routes.HOME else Routes.AUTH
                    navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    if (hasSession) {
                        // رفع أي إعلانات محفوظة أوفلاين لهذا المستخدم
                        launch { runCatching { OfflineSyncManager.syncPendingItems(client) } }
                    }
                }
            })
        }
        composable(Routes.AUTH) {
            val vm = remember(client) { AuthViewModel(client) }
            val authScope = rememberCoroutineScope()
            AuthScreen(
                viewModel = vm,
                onAuthenticated = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.AUTH) { inclusive = true } }
                    authScope.launch { runCatching { OfflineSyncManager.syncPendingItems(client) } }
                }
            )
        }
        composable(Routes.HOME) {
            val adminRepo = remember(client, context) { AdminRepository(client) }
            val contentRepo = remember(client) { ContentRepository(client) }
            val vm = remember(client) { HomeViewModel(platformRepo, adminRepo, contentRepo) }
            val scope = rememberCoroutineScope()
            HomeScreen(
                viewModel = vm,
                onOpenPlatform = { platform ->
                    if (platform.key == "auctions") navController.navigate(Routes.AUCTION_LIST)
                    else navController.navigate("listings/${platform.id}")
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onCreateListing = { navController.navigate(Routes.CREATE) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                onOpenChats = { navController.navigate(Routes.CHAT_LIST) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenMyListings = { navController.navigate(Routes.MY_LISTINGS) },
                onOpenAuctions = { navController.navigate(Routes.AUCTION_LIST) },
                onLogout = {
                    scope.launch {
                        client.auth.signOut()
                        navController.navigate(Routes.AUTH) { popUpTo(Routes.HOME) { inclusive = true } }
                    }
                }
            )
        }
        composable(Routes.FAVORITES) {
            val listingRepo = remember(client) { ListingRepository(client) }
            com.opengate.app.ui.screens.favorites.FavoritesScreen(
                listingRepo = listingRepo,
                onBack = { navController.popBackStack() },
                onOpenListing = { listingId -> navController.navigate("listing/$listingId") }
            )
        }
        composable(Routes.LISTINGS) { backStackEntry ->
            val platformId = backStackEntry.arguments?.getString("platformId") ?: ""
            val listingRepo = remember(client) { ListingRepository(client) }
            val vm = remember(platformId) { ListingsViewModel(platformId, platformRepo, listingRepo) }
            ListingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenListing = { listingId -> navController.navigate("listing/$listingId") }
            )
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getString("listingId") ?: ""
            val listingRepo = remember(client) { ListingRepository(client) }
            val reviewRepo = remember(client) { ReviewRepository(client) }
            val chatRepo = remember(client) { ChatRepository(client) }
            val vm = remember(listingId) { ListingDetailViewModel(listingId, client, platformRepo, listingRepo, reviewRepo) }
            ListingDetailScreen(
                viewModel = vm,
                chatRepo = chatRepo,
                onBack = { navController.popBackStack() },
                onOpenChat = { conversationId -> navController.navigate("chat/$conversationId") }
            )
        }
        composable(Routes.CREATE) {
            val listingRepo = remember(client) { ListingRepository(client) }
            val vm = remember(client) { CreateListingViewModel(client, platformRepo, listingRepo) }
            CreateListingScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAccountDeleted = {
                    navController.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(Routes.ADMIN) {
            val adminRepo = remember(client, context) { AdminRepository(client) }
            val vm = remember(client) { AdminViewModel(adminRepo) }
            AdminScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCatalog = { navController.navigate(Routes.ADMIN_CATALOG) },
                onOpenContent = { navController.navigate(Routes.ADMIN_CONTENT) },
                onOpenMembers = { navController.navigate(Routes.ADMIN_MEMBERS) },
                onOpenModeration = { navController.navigate(Routes.ADMIN_MODERATION) }
            )
        }
        composable(Routes.ADMIN_MEMBERS) {
            val adminRepo = remember(client, context) { AdminRepository(client) }
            val vm = remember(client) { AdminViewModel(adminRepo) }
            com.opengate.app.ui.screens.admin.AdminMembersScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ADMIN_CATALOG) {
            val adminRepo = remember(client) { AdminRepository(client) }
            val vm = remember(client) { AdminCatalogViewModel(adminRepo) }
            AdminCatalogScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_MODERATION) {
            val vm = remember(client) { com.opengate.app.ui.screens.admin.ModerationViewModel(com.opengate.app.data.repository.ListingRepository(client)) }
            com.opengate.app.ui.screens.admin.ModerationScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenListing = { listingId -> navController.navigate("listing/$listingId") }
            )
        }
        composable(Routes.MY_LISTINGS) {
            val vm = remember(client) { com.opengate.app.ui.screens.mylistings.MyListingsViewModel(client, com.opengate.app.data.repository.ListingRepository(client)) }
            com.opengate.app.ui.screens.mylistings.MyListingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenListing = { listingId -> navController.navigate("listing/$listingId") }
            )
        }
        composable(Routes.ADMIN_CONTENT) {
            val adminRepo = remember(client) { AdminRepository(client) }
            val vm = remember(client) { AdminContentViewModel(adminRepo) }
            AdminContentScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.CHAT_LIST) {
            val chatRepo = remember(client) { ChatRepository(client) }
            val vm = remember(client) { ChatListViewModel(client, chatRepo) }
            ChatListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenConversation = { id -> navController.navigate("chat/$id") }
            )
        }
        composable(Routes.CHAT) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val chatRepo = remember(client) { ChatRepository(client) }
            val vm = remember(conversationId) { ChatViewModel(conversationId, client, chatRepo) }
            ChatScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.AUCTION_LIST) {
            val auctionRepo = remember(client) { AuctionRepository(client) }
            val vm = remember(client) { AuctionListViewModel(auctionRepo, client) }
            AuctionListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenAuction = { id -> navController.navigate("auction/$id") }
            )
        }
        composable(Routes.AUCTION) { backStackEntry ->
            val auctionId = backStackEntry.arguments?.getString("auctionId") ?: ""
            val auctionRepo = remember(client) { AuctionRepository(client) }
            val vm = remember(auctionId) { AuctionViewModel(auctionId, client, auctionRepo) }
            AuctionScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
