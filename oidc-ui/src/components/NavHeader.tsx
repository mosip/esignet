export default function NavHeader() {
  return (
    <nav
      className="bg-white border-gray-500 md:px-[4rem] py-2 px-[0.5rem] navbar-header"
      id="navbar-header"
    >
      <div className="flex h-full items-center justify-between">
        <div className="sm:ml-8 ml-1">
          <img className="brand-logo" alt="brand_logo" />
        </div>
      </div>
    </nav>
  );
}
