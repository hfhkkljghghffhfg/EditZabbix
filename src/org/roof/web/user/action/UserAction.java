package org.roof.web.user.action;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.roof.commons.Md5Generator;
import org.roof.commons.SysConstants;
import org.roof.roof.dataaccess.api.Page;
import org.roof.roof.dataaccess.api.PageUtils;
import org.roof.spring.Result;
import org.roof.web.resource.entity.Module;
import org.roof.web.role.entity.BaseRole;
import org.roof.web.role.entity.Role;
import org.roof.web.role.service.api.IRoleService;
import org.roof.web.user.entity.User;
import org.roof.web.user.service.api.BaseUserContext;
import org.roof.web.user.service.api.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.alibaba.fastjson.JSONPath;

@Controller
@RequestMapping("userAction")
@SessionAttributes("userName")
public class UserAction {

	// private IMenuFilter menuFilter;
	private IUserService userService;
	private IRoleService roleService;

	@RequestMapping("/goLogin")
	public String goLogin(String errorCode, Model model) {
		model.addAttribute("errorCode", errorCode == null ? "null" : "'" + errorCode + "'");
		return "/roof-web/web/user/user_goLogin.jsp";
	}

	@RequestMapping("/goMain")
	public String goMain(Model model, HttpServletRequest request, HttpSession httpSession) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		// Menu menu = menuFilter.doFilter(1L, authentication);
		Map<Class<?>, String[]> map = new HashMap<Class<?>, String[]>();
		// map.put(Menu.class, new String[] { "parent" });
		map.put(Module.class, new String[] { "parent", "baseRole", "parameters", "returnFields" });
		// String s = JSONObject.fromObject(menu,
		// Result.createJsonConfig(map)).toString();
		// model.addAttribute("menuJson", s);
		return "/roof-web/web/user/user_goMain.jsp";
	}

	/**
	 * 登录验证
	 * 
	 * @return
	 */
	@RequestMapping("/login")
	public @ResponseBody Result login(User user, Model model,HttpSession session,HttpServletRequest request
			,HttpServletResponse response) {
		try {

			if (userService.isLoginError(user.getUsername())) {
				return new Result(Result.FAIL, "30分钟之内登录失败次数过多，请稍后再试");
			}
			User loginUser = userService.login(user);
			Cookie ck = getCookieByName(request,user.getUsername());
			if (loginUser == null) {
				int cnt = 0;
				if(ck == null){
					ck = new Cookie(user.getUsername(),"1");
					ck.setMaxAge(2*60); 
					//设置路径，这个路径即该工程下都可以访问该cookie 如果不设置路径，那么只有设置该cookie路径及其子路径可以访问
//					ck.setPath("/");
					ck.setPath(request.getContextPath()+"/");
					response.addCookie(ck);
					cnt = 1;
				}else{
					cnt = Integer.parseInt(ck.getValue()) + 1;
					ck.setValue(String.valueOf(cnt));
					ck.setMaxAge(2*60);
//					ck.setPath("/");
					ck.setPath(request.getContextPath()+"/");
					response.addCookie(ck);
				}

				/*
				 * 1.从数据库读取该用户的数据
				 * 2.如果没有,则新建一条记录,将登陆错误次数设置为1
				 * 3.如果有,将登陆错误次数+1,
				 * 4.返回登陆错误次数
				 * 5.如果登陆次数 = 已知允许次数,将该用户账号锁定XX分钟.
				 * 6.XX分钟到了,自动清除登陆失败信息
				 * */
//				int cnt = Integer.valueOf(session.getAttribute("loginErrorCnt").toString());
//				session.setAttribute("loginErrorCnt", cnt + 1);
				return new Result(Result.FAIL, "用户名或者密码错误，30分钟之内剩余"
						+ (SysConstants.getAllowableErrorCount() - cnt)
						+ "次!");
			} else {
				if(null != ck){
					ck.setValue(null);
					ck.setMaxAge(0);
					ck.setPath("/");
					response.addCookie(ck);
				}
				model.addAttribute("userName", user.getUsername());//将用户名保存在session中.
				return new Result("用户登录成功!");
			}
			// TODO userService.saveLoginLog(loginUser, user);
		} catch (Exception e) {
			return new Result(Result.FAIL, e.getMessage());
		}
	}

	@RequestMapping("/list")
	public String list(User user, HttpServletRequest request, Model model) {
		Page page = PageUtils.createPage(request);
		page = userService.page(page, user);
		Map<String, Long> pageBar = PageUtils.createPagePar(page);
		model.addAttribute("page", page);
		model.addAttribute("pageBar", pageBar);
		return "/roof-web/web/user/user_list.jsp";
	}

	@RequestMapping("/list_select")
	public String list_select(User user, HttpServletRequest request, Model model) {
		Page page = PageUtils.createPage(request);
		page = userService.page(page, user);
		Map<String, Long> pageBar = PageUtils.createPagePar(page);
		model.addAttribute("page", page);
		model.addAttribute("pageBar", pageBar);
		Md5Generator md5Generator = new Md5Generator();
		model.addAttribute("defPwd", md5Generator.calcmd5(SysConstants.DEFAULT_PWD).toUpperCase());
		return "/roof-web/web/user/user_list_select.jsp";
	}

	public String detail() {
		return null;
	}

	@RequestMapping("/update")
	public @ResponseBody Result update(User user, @RequestParam("rolesIds") Long[] rolesIds, Model model) {
		if (user != null) {
			user.setPassword(null);
			if (rolesIds != null) {
				Set<BaseRole> roles = new HashSet<BaseRole>();
				for (Long roleId : rolesIds) {
					roles.add(new Role(roleId, null));
				}
				user.setRoles(roles);
			}
			if (!JSONPath.contains(user, "$.org.id")) {
				user.setOrg(null);
			}
			userService.update(user);
			return new Result("保存成功!");
		} else {
			return new Result("数据传输失败!");
		}
	}

	@RequestMapping("/update_pwd")
	public @ResponseBody Result update_pwd(User user, Model model) {
		if (user != null) {
			userService.updateIgnoreNull(user);
			return new Result("修改成功!");
		} else {
			return new Result("修改失败!");
		}
	}

	@RequestMapping("/update_pwd_page")
	public String update_pwd_page(HttpServletRequest request, Model model) {
		UserDetails user = (UserDetails) BaseUserContext.getCurrentUser(request);
		model.addAttribute("user", user);
		return "/roof-web/web/user/user_update_pwd_page.jsp";
	}

	@RequestMapping("/update_page")
	public String update_page(Long id, Model model) {
		User user = userService.load(id);
		List<Role> roleses = roleService.loadAll();
		model.addAttribute("roleses", roleses);
		model.addAttribute("user", user);
		return "/roof-web/web/user/user_update_page.jsp";
	}

	@RequestMapping("/create")
	public @ResponseBody Result create(User user, @RequestParam("rolesIds") Long[] rolesIds) {
		if (user != null) {
			if (rolesIds != null) {
				Set<BaseRole> roles = new HashSet<BaseRole>();
				for (Long roleId : rolesIds) {
					roles.add(new Role(roleId, null));
				}
				user.setRoles(roles);
			}
			user.setCreate_date(new Date());
			if (!JSONPath.contains(user, "$.org.id")) {
				user.setOrg(null);
			}
			userService.save(user);
			return new Result("保存成功!");
		} else {
			return new Result("数据传输失败!");
		}
	}

	@RequestMapping("/create_page")
	public String create_page(Long id, Model model) {
		List<Role> roleses = roleService.loadAll();
		model.addAttribute("roleses", roleses);
		return "/roof-web/web/user/user_create_page.jsp";
	}

	@RequestMapping("/delete")
	public @ResponseBody Result delete(Long id) {
		User user = new User();
		user.setId(id);
		userService.delete(user);
		return new Result("删除成功!");
	}

	@RequestMapping("/sameUsername")
	public @ResponseBody Boolean sameUsername(String username, Long id) {
		return !userService.sameUsername(id, username);
	}

	@Autowired(required = true)
	public void setUserService(@Qualifier("userService") IUserService userService) {
		this.userService = userService;
	}

	@Autowired(required = true)
	public void setRoleService(@Qualifier("roleService") IRoleService roleService) {
		this.roleService = roleService;
	}
	
	
	/**
	 * 根据名字获取cookie
	 * @param request
	 * @param name cookie名字
	 * @return
	 */
	public  Cookie getCookieByName(HttpServletRequest request,String name){
	    Map<String,Cookie> cookieMap = ReadCookieMap(request);
	    if(cookieMap.containsKey(name)){
	        Cookie cookie = (Cookie)cookieMap.get(name);
	        return cookie;
	    }else{
	        return null;
	    }   
	}
	 
	 
	 
	/**
	 * 将cookie封装到Map里面
	 * @param request
	 * @return
	 */
	private Map<String,Cookie> ReadCookieMap(HttpServletRequest request){  
	    Map<String,Cookie> cookieMap = new HashMap<String,Cookie>();
	    Cookie[] cookies = request.getCookies();
	    if(null!=cookies){
	        for(Cookie cookie : cookies){
	            cookieMap.put(cookie.getName(), cookie);
	        }
	    }
	    return cookieMap;
	}
}
