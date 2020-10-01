package com.citi.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.citi.json.*;
public class OrderBusinessLogic{

	List<OrderJson> orders;
	List<TradeJson> tradedOrders; 
	List<OrderJson> finalOrderList;
	
	static Logger logger1 = LogManager.getLogger(OrderBusinessLogic.class.getName());
	public OrderBusinessLogic()
	{
		finalOrderList = new ArrayList<OrderJson>();
		logger1.info("In ctor of OrderBusinessLogic");
	}
	// Function to perform actual order matching
	public List<TradeJson> startMatching(List<OrderJson> ord)
	{
		List<OrderJson> buyList = new ArrayList<OrderJson>();       // List to store buy orders
		List<OrderJson> sellList = new ArrayList<OrderJson>();      // List to store sell orders
		int tradeId=1;
		
		tradedOrders = new ArrayList<TradeJson>();
		logger1.info("In start Matching function");
		orders = ord;												// Orders fetched from the database
		
		// STEP 1 : Running a loop to segregate the fetched list into buyList and sellList lists
		for(OrderJson o : orders)
		{
			if(o.getBuyOrSell().equalsIgnoreCase("BUY"))
				buyList.add(o);
			else
				sellList.add(o);
		}
		
		System.out.println("Buy List :");
		for(OrderJson buy : buyList)
		{
			System.out.println(buy.toString());
		}
		
		System.out.println("\nSell List : ");
		for(OrderJson sell : sellList)
		{
			System.out.println(sell.toString());
		}		
		
		
		// STEP 2 : Sorting both buyList and sellList on the basis of time
		Comparator<OrderJson> compareByTime = (OrderJson o1, OrderJson o2) -> o1.getOrderTime().compareTo(o2.getOrderTime());
		 
		Collections.sort(buyList, compareByTime);
		Collections.sort(sellList,compareByTime);
		
		
		List<OrderJson> lessSellTime = new ArrayList<OrderJson>();
		List<OrderJson> lessBuyTime = new ArrayList<OrderJson>();
		
		// STEP 3 : Comparing each buy order with every sell order for order matching
		for(OrderJson buy : buyList) {
			if(buy.getOrderType().equalsIgnoreCase("LIMIT"))
			{
				System.out.println("In buy limit ... ");
				Date buyDate = buy.getOrderTime();
				long buyTime = buyDate.getTime();
				
				for(OrderJson sell : sellList)
				{
					Date sellDate = sell.getOrderTime();     
					long sellTime = sellDate.getTime();
					
					
					// Checking if one buy order has been placed before any sell order ... by comparing orderTime
					if(sellTime <= buyTime)
					{
						// If sell order is placed before buy order then comparing the prices of buy and sell order
						if(buy.getPrice() >= sell.getPrice() && sell.getOrderStatus().equalsIgnoreCase("PENDING") && !sell.getOrderType().equalsIgnoreCase("MARKET") && buy.getOrderStatus().equalsIgnoreCase("PENDING") )
						{
							lessSellTime.add(sell);    // adding all the sell orders whose prices are less than buy order
						}
					}				
				}
				if(!lessSellTime.isEmpty())    
				{
					// Sorting the sell orders that have less price than the buy order on the basis of price
					Comparator<OrderJson> compareByPrice = (OrderJson o1, OrderJson o2) -> o1.getPrice().compareTo(o2.getPrice());
					Collections.sort(lessSellTime,compareByPrice);
					
					long buyQty = buy.getQuantity();   
					 
					for(OrderJson lst : lessSellTime) {
						long sellQty = lst.getQuantity();  
						buyQty = buy.getQuantity();    
						if(buyQty == sellQty)
						{
							buy.setOrderStatus("EXECUTED");
							lst.setOrderStatus("EXECUTED");
							
							tradedOrders.add(new TradeJson(tradeId,buy.getOrderTime(),buyQty,lst.getPrice(),buy.getId(),lst.getId()) );
							tradeId++;
							System.out.println("1. Trade between "+buy.getId() +" and "+lst.getId());
							buy.setQuantity(0L);
							lst.setQuantity(0L);
							
						}
						else if(buyQty < sellQty)
						{
							buy.setOrderStatus("EXECUTED");
							tradedOrders.add(new TradeJson(tradeId,buy.getOrderTime(),buyQty,lst.getPrice(),buy.getId(),lst.getId()));
							tradeId++;
							System.out.println("2. Trade between "+buy.getId() +" and "+lst.getId());
							lst.setQuantity(sellQty-buyQty);
							buy.setQuantity(0L);
						}
						else    // Sell Qty is less
						{
							tradedOrders.add(new TradeJson(tradeId,buy.getOrderTime(),lst.getQuantity(),lst.getPrice(),buy.getId(),lst.getId()));
							tradeId++;
							lst.setOrderStatus("EXECUTED");
							System.out.println("3. Trade between "+buy.getId() +" and "+lst.getId());
							buy.setQuantity(buyQty-lst.getQuantity());
							lst.setQuantity(0L);
						}
					}
					
				lessSellTime.clear();
			   }
			}
			else if(buy.getOrderType().equalsIgnoreCase("MARKET") )
			{
				System.out.println("In buy market ... ");
				Date buyDate = buy.getOrderTime();
				long buyTime = buyDate.getTime();
				
				List<OrderJson> lessTime = new ArrayList<OrderJson>();
				long totalQty = 0L;
				for(OrderJson sell:sellList)    
				{
					Date sellDate = sell.getOrderTime();
					long sellTime = sellDate.getTime();
					if(sellTime <= buyTime && sell.getOrderType().equalsIgnoreCase("LIMIT") && sell.getOrderStatus().equalsIgnoreCase("PENDING") && buy.getOrderStatus().equalsIgnoreCase("PENDING"))
					{
						lessTime.add(sell);
					}
				}
				for(OrderJson lt : lessTime)
				{
					totalQty += lt.getQuantity();
				}
				if(totalQty >= buy.getQuantity())
				{
					Comparator<OrderJson> compareByPrice = (OrderJson o1, OrderJson o2) -> o1.getPrice().compareTo(o2.getPrice());
					Collections.sort(lessTime,compareByPrice);
					long buyQt = buy.getQuantity();
					for(OrderJson lt : lessTime)
					{
						long sellQt = lt.getQuantity();
						buyQt = buy.getQuantity();
						if(buyQt > 0)
						{
							if(buyQt == sellQt)
							{
								buy.setOrderStatus("EXECUTED");
								lt.setOrderStatus("EXECUTED");
								
								buy.setQuantity(0L);
								lt.setQuantity(0L);
								tradedOrders.add(new TradeJson(tradeId,buy.getOrderTime(),buyQt,lt.getPrice(),buy.getId(),lt.getId()));
								tradeId++;
								System.out.println("4. Trade between "+buy.getId() +" and "+lt.getId());
								break;
							}
							else if(buyQt < sellQt)
							{
								lt.setQuantity((sellQt-buyQt));
								buy.setQuantity(0L);
								
								buy.setOrderStatus("EXECUTED");
								
								tradedOrders.add(new TradeJson(tradeId,buy.getOrderTime(),buyQt,lt.getPrice(),buy.getId(),lt.getId()));
								tradeId++;
								System.out.println("5. Trade between "+buy.getId() +" and "+lt.getId());
							}
							else //sell qty is smaller
							{
								lt.setQuantity(0L);
								buy.setQuantity(buyQt-sellQt);
								
								lt.setOrderStatus("EXECUTED");
								tradedOrders.add(new TradeJson(tradeId,buy.getOrderTime(),sellQt,lt.getPrice(),buy.getId(),lt.getId()));
								System.out.println("6. Trade between "+buy.getId() +" and "+lt.getId());
							}
						}
					}
					
				}
				else
				{
					buy.setOrderStatus("REJECTED");
				}
				lessTime.clear();
				
			}
	}


		
		
		for(OrderJson sell : sellList) {
			if(sell.getOrderType().equalsIgnoreCase("LIMIT"))
			{
				System.out.println("In sell limit ... ");
				Date sellDate = sell.getOrderTime();
				long sellTime = sellDate.getTime();
				
				for(OrderJson buy : buyList)
				{
					Date buyDate = buy.getOrderTime();     
					long buyTime = buyDate.getTime();
					
					
					// Checking if one buy order has been placed before any sell order ... by comparing orderTime
					if(buyTime <= sellTime)
					{
						// If sell order is placed before buy order then comparing the prices of buy and sell order
						if(sell.getPrice() <= buy.getPrice() && sell.getOrderStatus().equalsIgnoreCase("PENDING") && buy.getOrderStatus().equalsIgnoreCase("PENDING") )
						{
							lessBuyTime.add(buy);    // adding all the sell orders whose prices are less than buy order
						}
					}				
				}
				if(!lessBuyTime.isEmpty())    
				{
					// Sorting the sell orders that have less price than the buy order on the basis of price
					Comparator<OrderJson> compareByPrice = (OrderJson o1, OrderJson o2) -> o1.getPrice().compareTo(o2.getPrice());
					compareByPrice = Collections.reverseOrder();
					Collections.sort(lessBuyTime,compareByPrice); 
					
					long sellQty = sell.getQuantity();   
					 
					for(OrderJson lbt : lessBuyTime) { 
						long buyQty = lbt.getQuantity();  
						sellQty = sell.getQuantity();    
						if(buyQty == sellQty)
						{
							sell.setOrderStatus("EXECUTED");
							lbt.setOrderStatus("EXECUTED");
							
							tradedOrders.add(new TradeJson(tradeId,sell.getOrderTime(),buyQty,lbt.getPrice(),lbt.getId(),sell.getId()) );
							System.out.println("7. Trade executed for equal qty between : "+lbt.getId() + "and" + sell.getId());
							tradeId++;
							lbt.setQuantity(0L);
							sell.setQuantity(0L);
							
						}
						else if(buyQty < sellQty)
						{
							lbt.setOrderStatus("EXECUTED");
							tradedOrders.add(new TradeJson(tradeId,sell.getOrderTime(),buyQty,lbt.getPrice(),lbt.getId(),sell.getId()));
							tradeId++;
							System.out.println("8. Trade executed for less buy qty between : "+lbt.getId() + "and" + sell.getId());
							sell.setQuantity(sellQty-buyQty);
							lbt.setQuantity(0L);
							
						}
						else    
						{
							tradedOrders.add(new TradeJson(tradeId,sell.getOrderTime(),sell.getQuantity(),lbt.getPrice(),lbt.getId(),sell.getId()));
							sell.setOrderStatus("EXECUTED");
							tradeId++;
							System.out.println("9. Trade executed for less sell qty between : "+lbt.getId() + "and" + sell.getId());
							System.out.println("BuyQty : "+buyQty+"  SellQty : "+sellQty);
							lbt.setQuantity(buyQty-sellQty);
							sell.setQuantity(0L);
						}
					}
					
				lessBuyTime.clear();
			}
		}
			else if(sell.getOrderType().equalsIgnoreCase("MARKET"))
			{
				System.out.println("In sell market ... ");
				Date sellDate = sell.getOrderTime();
				long sellTime = sellDate.getTime();
				
				List<OrderJson> lessTime = new ArrayList<OrderJson>();
				long totalQty = 0L;
				for(OrderJson buy:buyList)    
				{
					Date buyDate = buy.getOrderTime();
					long buyTime = buyDate.getTime();
					if(buyTime <= sellTime && buy.getOrderType().equalsIgnoreCase("LIMIT") && sell.getOrderStatus().equalsIgnoreCase("PENDING") && buy.getOrderStatus().equalsIgnoreCase("PENDING"))
					{
						lessTime.add(buy);
					}
				}
				for(OrderJson lt : lessTime)
				{
					totalQty += lt.getQuantity();
				}
				if(totalQty >= sell.getQuantity())
				{
					Comparator<OrderJson> compareByPrice = (OrderJson o1, OrderJson o2) -> o1.getPrice().compareTo(o2.getPrice());
					compareByPrice = Comparator.reverseOrder();
					Collections.sort(lessTime,compareByPrice);
					
					long sellQt = sell.getQuantity();
					for(OrderJson lt : lessTime)
					{
						sellQt = sell.getQuantity();
						long buyQt = lt.getQuantity();
						if(sellQt > 0)
						{
							if(buyQt == sellQt)
							{
								lt.setOrderStatus("EXECUTED");
								sell.setOrderStatus("EXECUTED");
								
								lt.setQuantity(0L);
								sell.setQuantity(0L);
								tradedOrders.add(new TradeJson(tradeId,sell.getOrderTime(),buyQt,lt.getPrice(),lt.getId(),sell.getId()));
								tradeId++;
								System.out.println("10. Trade between "+lt.getId() +" and "+sell.getId());
								break;
							}
							else if(sellQt < buyQt)
							{
								lt.setQuantity((buyQt-sellQt));
								sell.setQuantity(0L);
								
								sell.setOrderStatus("EXECUTED");
								
								tradedOrders.add(new TradeJson(tradeId,sell.getOrderTime(),sellQt,lt.getPrice(),lt.getId(),sell.getId()));
								tradeId++;
								System.out.println("11. Trade between "+lt.getId() +" and "+sell.getId());
							}
							else //buy qty is smaller
							{
								lt.setQuantity(0L);
								sell.setQuantity(sellQt-buyQt);
								
								lt.setOrderStatus("EXECUTED");
								tradedOrders.add(new TradeJson(tradeId,sell.getOrderTime(),buyQt,lt.getPrice(),lt.getId(),sell.getId()));
								tradeId++;
								System.out.println("12. Trade between "+lt.getId() +" and "+sell.getId());
							}
						}
					}
					
				}
				else
				{
					sell.setOrderStatus("REJECTED");
				}
				lessTime.clear();
			}		
	}
		System.out.println(" **** TRADE EXECUTED **** ");
		for(TradeJson trade : tradedOrders)
		{
			System.out.println(trade.toString());
		}
		
		System.out.println("Buy List :");
		for(OrderJson buy : buyList)
		{
			System.out.println(buy.toString());
			finalOrderList.add(buy);
		}
		
		System.out.println("\nSell List : ");
		for(OrderJson sell : sellList)
		{
			System.out.println(sell.toString());
			finalOrderList.add(sell);
		}

		
		return tradedOrders;
	}

	public List<OrderJson> getFinalOrderList()
	{
		return this.finalOrderList;
	}

}
